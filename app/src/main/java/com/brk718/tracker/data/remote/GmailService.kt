package com.brk718.tracker.data.remote

import android.content.Context
import android.content.Intent
import com.brk718.tracker.domain.EmailParser
import com.brk718.tracker.domain.ParsedShipment
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GmailService @Inject constructor(
    @ApplicationContext private val context: Context
) : EmailService {

    private var account: GoogleSignInAccount? = null
    private var gmailClient: Gmail? = null

    val signInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(GmailScopes.GMAIL_READONLY))
            .requestServerAuthCode(com.brk718.tracker.BuildConfig.GMAIL_CLIENT_ID)
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    val signInIntent: Intent get() = signInClient.signInIntent

    override suspend fun connect() {
        // Se llama después de obtener el resultado del sign-in intent
        account = GoogleSignIn.getLastSignedInAccount(context)
        account?.let { setupGmailClient(it) }
    }

    fun handleSignInResult(acct: GoogleSignInAccount) {
        account = acct
        setupGmailClient(acct)
    }

    private fun setupGmailClient(acct: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(GmailScopes.GMAIL_READONLY)
        )
        credential.selectedAccount = acct.account
        gmailClient = Gmail.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Tracker").build()
    }

    override suspend fun disconnect() {
        signInClient.signOut()
        account = null
        gmailClient = null
    }

    override suspend fun isConnected(): Boolean {
        if (account == null) {
            account = GoogleSignIn.getLastSignedInAccount(context)
            account?.let { setupGmailClient(it) }
        }
        return account != null && gmailClient != null
    }

    fun getAccountEmail(): String? = account?.email

    override suspend fun fetchRecentTrackingNumbers(): List<ParsedShipment> = withContext(Dispatchers.IO) {
        val gmail = gmailClient ?: return@withContext emptyList()

        try {
            // Buscar emails de envío/tracking de los últimos 30 días
            val query = buildString {
                append("newer_than:30d ")
                append("(subject:envío OR subject:enviado OR subject:shipped OR subject:tracking ")
                append("OR subject:pedido OR subject:despacho OR subject:entrega OR subject:guía ")
                append("OR from:amazon OR from:mercadolibre OR from:rappi ")
                append("OR from:fedex OR from:ups OR from:dhl OR from:usps ")
                append("OR from:coordinadora OR from:servientrega OR from:interrapidisimo ")
                append("OR from:deprisa OR from:tcc.com.co OR from:envia.com OR from:472.com.co)")
            }

            val messages = gmail.users().messages().list("me")
                .setQ(query)
                .setMaxResults(50)
                .execute()
                .messages ?: return@withContext emptyList()

            val results = mutableListOf<ParsedShipment>()

            for (msg in messages) {
                try {
                    val fullMessage = gmail.users().messages().get("me", msg.id)
                        .setFormat("full")
                        .execute()

                    // Obtener sender
                    val sender = fullMessage.payload?.headers
                        ?.find { it.name.equals("From", ignoreCase = true) }
                        ?.value

                    // Obtener subject
                    val subject = fullMessage.payload?.headers
                        ?.find { it.name.equals("Subject", ignoreCase = true) }
                        ?.value ?: ""

                    // Extraer texto del cuerpo
                    val bodyText = extractBodyText(fullMessage.payload)

                    // Parsear tracking numbers
                    val combined = "$subject\n$bodyText"
                    val parsed = EmailParser.findTrackingNumbers(combined, sender)
                    parsed.forEach { p ->
                        if (results.none { it.trackingNumber == p.trackingNumber }) {
                            results.add(p.copy(title = subject.take(60)))
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("GmailService", "Error parsing message ${msg.id}: ${e.message}")
                }
            }

            results
        } catch (e: Exception) {
            android.util.Log.e("GmailService", "Error fetching emails: ${e.message}")
            emptyList()
        }
    }

    private fun extractBodyText(payload: com.google.api.services.gmail.model.MessagePart?): String {
        if (payload == null) return ""

        val sb = StringBuilder()

        // Si tiene body directo
        payload.body?.data?.let { data ->
            try {
                val decoded = String(Base64.getUrlDecoder().decode(data))
                if (payload.mimeType == "text/html") {
                    sb.append(htmlToPlainText(decoded))
                } else {
                    sb.append(decoded)
                }
            } catch (_: Exception) {}
        }

        // Recursivamente en las partes
        payload.parts?.forEach { part ->
            when (part.mimeType) {
                "text/plain" -> {
                    part.body?.data?.let { data ->
                        try {
                            sb.append(String(Base64.getUrlDecoder().decode(data)))
                        } catch (_: Exception) {}
                    }
                }
                "text/html" -> {
                    part.body?.data?.let { data ->
                        try {
                            val html = String(Base64.getUrlDecoder().decode(data))
                            sb.append(htmlToPlainText(html))
                        } catch (_: Exception) {}
                    }
                }
            }
            // Recursar para multipart
            if (part.mimeType?.startsWith("multipart/") == true) {
                sb.append(extractBodyText(part))
            }
        }

        return sb.toString()
    }

    /**
     * Convierte HTML a texto plano preservando el contenido de texto visible.
     * Usa android.text.Html que ya viene incluido en el SDK de Android.
     */
    @Suppress("DEPRECATION")
    private fun htmlToPlainText(html: String): String {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_COMPACT).toString()
        } else {
            android.text.Html.fromHtml(html).toString()
        }
    }
}
