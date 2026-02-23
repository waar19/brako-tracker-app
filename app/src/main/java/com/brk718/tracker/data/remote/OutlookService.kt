package com.brk718.tracker.data.remote

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.Base64
import com.brk718.tracker.BuildConfig
import com.brk718.tracker.domain.EmailParser
import com.brk718.tracker.domain.ParsedShipment
import com.microsoft.identity.client.AcquireTokenParameters
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SilentAuthenticationCallback
import com.microsoft.identity.client.exception.MsalException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Implementación de [EmailService] para Outlook / Hotmail usando MSAL + Microsoft Graph API.
 *
 * Pre-requisito: registrar la app en Azure Portal y añadir OUTLOOK_CLIENT_ID a local.properties.
 *
 * Flujo:
 *  1. [signIn] lanza el browser OAuth de Microsoft y obtiene el access token.
 *  2. [fetchRecentTrackingNumbers] consulta /me/messages en Graph API (últimos 30 días)
 *     y reutiliza [EmailParser] para extraer guías del asunto + cuerpo.
 */
@Singleton
class OutlookService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) : EmailService {

    companion object {
        private const val TAG = "OutlookService"
        private val SCOPES = listOf("Mail.Read")
        private const val GRAPH_BASE =
            "https://graph.microsoft.com/v1.0/me/messages" +
            "?\$filter=receivedDateTime+ge+{DATE}" +
            "&\$select=subject,from,body" +
            "&\$top=50" +
            "&\$orderby=receivedDateTime+desc"
    }

    private var msalApp: ISingleAccountPublicClientApplication? = null
    private var currentAccount: IAccount? = null
    private var accessToken: String? = null

    // Guard: solo intentar restaurar la sesión una vez por ciclo de vida del proceso.
    // Evita llamadas MSAL redundantes cuando múltiples ViewModels llaman a connect().
    @Volatile private var sessionRestoreAttempted = false

    // ─── Inicialización MSAL ───────────────────────────────────────────────────

    /**
     * Crea (o devuelve) el cliente MSAL.
     * La configuración se escribe en un archivo temporal para que MSAL la lea,
     * usando el client_id cargado desde BuildConfig (local.properties).
     */
    private suspend fun getOrCreateApp(): ISingleAccountPublicClientApplication =
        msalApp ?: suspendCancellableCoroutine { cont ->
            if (BuildConfig.OUTLOOK_CLIENT_ID.isBlank()) {
                cont.resumeWithException(
                    IllegalStateException(
                        "OUTLOOK_CLIENT_ID no configurado. " +
                        "Añade OUTLOOK_CLIENT_ID=<tu_azure_client_id> a local.properties."
                    )
                )
                return@suspendCancellableCoroutine
            }
            try {
                val configFile = writeMsalConfigFile()
                PublicClientApplication.createSingleAccountPublicClientApplication(
                    context,
                    configFile,
                    object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                        override fun onCreated(application: ISingleAccountPublicClientApplication) {
                            msalApp = application
                            cont.resume(application)
                        }
                        override fun onError(exception: MsalException) {
                            cont.resumeWithException(exception)
                        }
                    }
                )
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        }

    /**
     * Escribe el JSON de configuración MSAL en un archivo temporal y retorna el File.
     * Esto permite construir la configuración dinámicamente desde BuildConfig.
     */
    private fun writeMsalConfigFile(): File {
        val sigHash = computeSignatureHash()
        // MSAL compara el redirect_uri con la versión URL-encoded del hash (= → %3D).
        // Si pasamos el hash sin encodear, MSAL falla con "redirect_uri doesn't match".
        val encodedHash = java.net.URLEncoder.encode(sigHash, "UTF-8")
        val json = JSONObject()
            .put("client_id", BuildConfig.OUTLOOK_CLIENT_ID)
            .put("authorization_user_agent", "DEFAULT")
            .put("redirect_uri", "msauth://com.brk718.tracker/$encodedHash")
            .put("account_mode", "SINGLE")
            .put("authorities", JSONArray().put(
                JSONObject()
                    .put("type", "AAD")
                    .put("audience", JSONObject()
                        .put("type", "AzureADandPersonalMicrosoftAccount")
                        .put("tenant_id", "common")
                    )
            ))
            .toString()

        val configFile = File(context.cacheDir, "msal_config.json")
        configFile.writeText(json)
        return configFile
    }

    /**
     * Calcula el hash SHA-1 en Base64 del certificado de firma de la app.
     * Debe coincidir con el Signature hash registrado en Azure → Authentication → Android.
     */
    @Suppress("DEPRECATION")
    private fun computeSignatureHash(): String {
        return try {
            val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, flags)
            val sigBytes = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pkgInfo.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.signatures?.firstOrNull()?.toByteArray()
            } ?: byteArrayOf()
            val digest = MessageDigest.getInstance("SHA-1").digest(sigBytes)
            Base64.encodeToString(digest, Base64.NO_WRAP or Base64.URL_SAFE)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "No se pudo calcular el signature hash: ${e.message}")
            "UNKNOWN"
        }
    }

    // ─── EmailService API ──────────────────────────────────────────────────────

    /**
     * Intenta obtener la cuenta ya guardada silenciosamente (sin interacción del usuario).
     * Se llama al iniciar la app para restaurar sesiones previas.
     */
    override suspend fun connect() {
        // No-op si ya hay token activo o si ya se intentó la restauración en este proceso
        if (accessToken != null || sessionRestoreAttempted) return
        sessionRestoreAttempted = true
        try {
            val restored = tryRestoreSession()
            android.util.Log.d(TAG, "connect() → sesión restaurada=$restored, " +
                "account=${currentAccount?.username}")
        } catch (e: Exception) {
            android.util.Log.w(TAG, "connect() excepción: ${e.message}")
        }
    }

    /**
     * Consulta a MSAL si hay una cuenta guardada y obtiene el token silenciosamente.
     * Clave para restaurar sesiones tras reinicio de la app o muerte del proceso.
     *
     * NOTA: el bug común de MSAL con Custom Tabs es que [signIn] puede devolver false
     * (onCancel) aunque el login fue exitoso — en ese caso los tokens SÍ están guardados
     * y esta función los recupera.
     */
    suspend fun tryRestoreSession(): Boolean {
        return try {
            val app = getOrCreateApp()
            suspendCancellableCoroutine { cont ->
                app.getCurrentAccountAsync(
                    object : ISingleAccountPublicClientApplication.CurrentAccountCallback {
                        override fun onAccountLoaded(activeAccount: IAccount?) {
                            if (activeAccount != null) {
                                currentAccount = activeAccount
                                // Esperar al token ANTES de resumir el coroutine
                                acquireTokenSilentlyAsync(app, activeAccount) { token ->
                                    accessToken = token
                                    android.util.Log.d(TAG,
                                        "tryRestoreSession: token=${if (token != null) "OK" else "null"}")
                                    cont.resume(token != null)
                                }
                            } else {
                                cont.resume(false)
                            }
                        }
                        override fun onAccountChanged(
                            priorAccount: IAccount?,
                            currentActiveAccount: IAccount?
                        ) {
                            cont.resume(false)
                        }
                        override fun onError(exception: MsalException) {
                            android.util.Log.w(TAG,
                                "tryRestoreSession error: ${exception.message}")
                            cont.resume(false)
                        }
                    }
                )
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "tryRestoreSession excepción: ${e.message}")
            false
        }
    }

    override suspend fun disconnect() {
        sessionRestoreAttempted = false   // permite restaurar sesión si vuelve a conectar
        try {
            val app = msalApp ?: return
            suspendCancellableCoroutine<Unit> { cont ->
                app.signOut(object : ISingleAccountPublicClientApplication.SignOutCallback {
                    override fun onSignOut() {
                        currentAccount = null
                        accessToken = null
                        cont.resume(Unit)
                    }
                    override fun onError(exception: MsalException) {
                        currentAccount = null
                        accessToken = null
                        cont.resume(Unit)
                    }
                })
            }
        } catch (e: Exception) {
            currentAccount = null
            accessToken = null
        }
    }

    override suspend fun isConnected(): Boolean = accessToken != null

    /**
     * Devuelve el email de la cuenta conectada, o null si no hay sesión activa.
     */
    fun getAccountEmail(): String? = currentAccount?.username

    // ─── Sign-in interactivo (OAuth browser flow) ──────────────────────────────

    /**
     * Lanza el browser OAuth de Microsoft y obtiene el access token.
     * Devuelve true si el sign-in fue exitoso, false si el usuario canceló.
     *
     * @param activity Activity activa — necesaria para lanzar el Custom Tab.
     */
    suspend fun signIn(activity: Activity): Boolean = withContext(Dispatchers.Main) {
        val app = getOrCreateApp()
        suspendCancellableCoroutine { cont ->
            val params = AcquireTokenParameters.Builder()
                .startAuthorizationFromActivity(activity)
                .withScopes(SCOPES)
                .withCallback(object : AuthenticationCallback {
                    override fun onSuccess(authenticationResult: IAuthenticationResult) {
                        currentAccount = authenticationResult.account
                        accessToken = authenticationResult.accessToken
                        android.util.Log.d(TAG, "Sign-in OK: ${currentAccount?.username}")
                        cont.resume(true)
                    }
                    override fun onError(exception: MsalException) {
                        android.util.Log.e(TAG, "Sign-in error: ${exception.message}")
                        cont.resumeWithException(exception)
                    }
                    override fun onCancel() {
                        android.util.Log.d(TAG, "Sign-in cancelado por el usuario")
                        cont.resume(false)
                    }
                })
                .build()
            app.acquireToken(params)
        }
    }

    // ─── Obtener emails y extraer guías ───────────────────────────────────────

    override suspend fun fetchRecentTrackingNumbers(): List<ParsedShipment> =
        withContext(Dispatchers.IO) {
            val token = accessToken ?: run {
                // Intentar renovar el token silenciosamente
                val app = msalApp ?: return@withContext emptyList()
                val account = currentAccount ?: return@withContext emptyList()
                accessToken = acquireTokenSilentlySuspend(app, account)
                accessToken ?: return@withContext emptyList()
            }

            try {
                val since = Instant.now().minus(30, ChronoUnit.DAYS).toString()
                val url = GRAPH_BASE.replace("{DATE}", since)

                val response = okHttpClient.newCall(
                    Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $token")
                        .header("Accept", "application/json")
                        .build()
                ).execute()

                if (response.code == 401) {
                    android.util.Log.w(TAG, "Token expirado (401). Desconectando.")
                    accessToken = null
                    return@withContext emptyList()
                }

                val body = response.body?.string() ?: return@withContext emptyList()
                response.close()

                val messagesArr: JSONArray = JSONObject(body).optJSONArray("value")
                    ?: return@withContext emptyList()

                val results = mutableListOf<ParsedShipment>()

                for (i in 0 until messagesArr.length()) {
                    try {
                        val msg = messagesArr.getJSONObject(i)
                        val subject = msg.optString("subject", "")
                        val from = msg.optJSONObject("from")
                            ?.optJSONObject("emailAddress")
                            ?.optString("address") ?: ""
                        val bodyContent = msg.optJSONObject("body")
                            ?.optString("content") ?: ""

                        val plainText = Jsoup.parse(bodyContent).text()
                        val combined = "$subject\n$plainText"

                        val parsed = EmailParser.findTrackingNumbers(combined, from)
                        parsed.forEach { p ->
                            if (results.none { it.trackingNumber == p.trackingNumber }) {
                                results.add(p.copy(title = subject.take(60)))
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "Error parseando mensaje $i: ${e.message}")
                    }
                }

                android.util.Log.d(TAG,
                    "Emails escaneados: ${messagesArr.length()}, guías: ${results.size}")
                results

            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error fetchRecentTrackingNumbers: ${e.message}", e)
                emptyList()
            }
        }

    // ─── Helpers de token silencioso ──────────────────────────────────────────

    private suspend fun acquireTokenSilentlySuspend(
        app: ISingleAccountPublicClientApplication,
        account: IAccount
    ): String? = suspendCancellableCoroutine { cont ->
        acquireTokenSilentlyAsync(app, account) { token -> cont.resume(token) }
    }

    private fun acquireTokenSilentlyAsync(
        app: ISingleAccountPublicClientApplication,
        account: IAccount,
        onResult: (String?) -> Unit
    ) {
        val authority = app.configuration.defaultAuthority?.authorityURL?.toString()
            ?: "https://login.microsoftonline.com/common"
        val params = AcquireTokenSilentParameters.Builder()
            .forAccount(account)
            .fromAuthority(authority)
            .withScopes(SCOPES)
            .withCallback(object : SilentAuthenticationCallback {
                override fun onSuccess(result: IAuthenticationResult) {
                    currentAccount = result.account
                    accessToken = result.accessToken
                    onResult(result.accessToken)
                }
                override fun onError(exception: MsalException) {
                    android.util.Log.w(TAG, "Token silencioso falló: ${exception.message}")
                    onResult(null)
                }
            })
            .build()
        app.acquireTokenSilentAsync(params)
    }
}
