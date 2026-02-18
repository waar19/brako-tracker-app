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
                append("OR subject:pedido OR subject:despacho OR subject:entrega ")
                append("OR from:amazon OR from:mercadolibre OR from:rappi ")
                append("OR from:fedex OR from:ups OR from:dhl OR from:usps)")
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
                sb.append(String(Base64.getUrlDecoder().decode(data)))
            } catch (_: Exception) {}
        }

        // Recursivamente en las partes
        payload.parts?.forEach { part ->
            if (part.mimeType?.startsWith("text/") == true) {
                part.body?.data?.let { data ->
                    try {
                        sb.append(String(Base64.getUrlDecoder().decode(data)))
                    } catch (_: Exception) {}
                }
            }
            // Recursar para multipart
            sb.append(extractBodyText(part))
        }

        return sb.toString()
    }
}
