package com.brk718.tracker.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.brk718.tracker.data.local.ShipmentWithEvents
import com.brk718.tracker.data.repository.ShipmentRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Genera una tarjeta PNG con el estado del envío y lanza el sistema de compartir.
 *
 * El diseño es completamente manual (Android Canvas) para no requerir
 * ninguna dependencia adicional. Usa colores hardcoded en blanco/gris
 * para que funcione bien tanto en modo claro como oscuro.
 */
object ShareCardGenerator {

    private const val CARD_WIDTH  = 900
    private const val CARD_HEIGHT = 480
    private const val CORNER_R    = 32f
    private const val PADDING     = 48f

    // Colores de la tarjeta (diseño claro siempre para imagen compartida)
    private const val BG_COLOR           = 0xFFFFFFFF.toInt()
    private const val PRIMARY_COLOR      = 0xFF2952CC.toInt()   // Indigo40
    private const val TEXT_PRIMARY       = 0xFF1A1B22.toInt()   // Neutral10
    private const val TEXT_SECONDARY     = 0xFF46464F.toInt()   // NeutralVar30
    private const val CHIP_BG_DELIVERED  = 0xFFDCFCE7.toInt()   // verde claro
    private const val CHIP_BG_TRANSIT    = 0xFFDBEAFE.toInt()   // azul claro
    private const val CHIP_BG_PROBLEM    = 0xFFFEE2E2.toInt()   // rojo claro
    private const val CHIP_BG_DEFAULT    = 0xFFE4E1EC.toInt()   // gris claro
    private const val CHIP_TEXT_DELIVERED = 0xFF166534.toInt()
    private const val CHIP_TEXT_TRANSIT   = 0xFF1E40AF.toInt()
    private const val CHIP_TEXT_PROBLEM   = 0xFF991B1B.toInt()
    private const val CHIP_TEXT_DEFAULT   = 0xFF46464F.toInt()
    private const val DIVIDER_COLOR      = 0xFFE4E1EC.toInt()
    private const val ACCENT_STRIP       = 0xFF2952CC.toInt()   // barra azul top

    fun shareShipmentAsImage(context: Context, shipmentWithEvents: ShipmentWithEvents) {
        val bitmap = drawCard(context, shipmentWithEvents)

        // Guardar en caché
        val imagesDir = File(context.cacheDir, "images").apply { mkdirs() }
        val imageFile = File(imagesDir, "shipment_status.png")
        imageFile.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
        bitmap.recycle()

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_TEXT,
                "📦 ${shipmentWithEvents.shipment.title}\nEstado: ${shipmentWithEvents.shipment.status}"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Compartir estado del envío"))
    }

    private fun drawCard(context: Context, swe: ShipmentWithEvents): Bitmap {
        val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // ── Fondo blanco con esquinas redondeadas ───────────────────────────
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BG_COLOR }
        canvas.drawRoundRect(
            RectF(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat()),
            CORNER_R, CORNER_R, bgPaint
        )

        // ── Barra de acento superior ────────────────────────────────────────
        val accentPaint = Paint().apply { color = ACCENT_STRIP }
        canvas.drawRoundRect(
            RectF(0f, 0f, CARD_WIDTH.toFloat(), 12f),
            CORNER_R, CORNER_R, accentPaint
        )
        canvas.drawRect(RectF(0f, 6f, CARD_WIDTH.toFloat(), 12f), accentPaint)

        // ── Avatar / círculo del transportista ──────────────────────────────
        val carrier = swe.shipment.carrier
        val carrierName = ShipmentRepository.displayName(carrier)
        val initial = carrierName.firstOrNull()?.uppercaseChar() ?: '?'

        val avatarColor = when (carrier.lowercase()) {
            "amazon"                  -> 0xFFFF9900.toInt()
            "ups"                     -> 0xFF351C15.toInt()
            "fedex"                   -> 0xFF4D148C.toInt()
            "dhl"                     -> 0xFFFFCC00.toInt()
            "interrapidisimo-scraper" -> 0xFFE30613.toInt()
            "coordinadora"            -> 0xFF003087.toInt()
            "servientrega"            -> 0xFF009B48.toInt()
            "envia-co"                -> 0xFFFF6B00.toInt()
            else                      -> PRIMARY_COLOR
        }
        val avatarRadius = 44f
        val avatarCx = PADDING + avatarRadius
        val avatarCy = CARD_HEIGHT / 2f

        val avatarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = avatarColor }
        canvas.drawCircle(avatarCx, avatarCy, avatarRadius, avatarPaint)

        val avatarTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (carrier.lowercase() == "dhl") 0xFF333333.toInt() else 0xFFFFFFFF.toInt()
            textSize = 40f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            initial.toString(),
            avatarCx,
            avatarCy - (avatarTextPaint.ascent() + avatarTextPaint.descent()) / 2f,
            avatarTextPaint
        )

        // ── Contenido de texto ───────────────────────────────────────────────
        val textX = avatarCx + avatarRadius + PADDING

        // Transportista en pequeño
        val carrierLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PRIMARY_COLOR
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText(carrierName.uppercase(), textX, avatarCy - 96f, carrierLabelPaint)

        // Título del envío
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_PRIMARY
            textSize = 44f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.LEFT
        }
        val title = swe.shipment.title.let {
            if (it.length > 28) it.take(25) + "…" else it
        }
        canvas.drawText(title, textX, avatarCy - 36f, titlePaint)

        // Tracking number
        val trackingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_SECONDARY
            textSize = 26f
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText(swe.shipment.trackingNumber, textX, avatarCy + 10f, trackingPaint)

        // ── Chip de estado ───────────────────────────────────────────────────
        val status = swe.shipment.status
        val statusLower = status.lowercase()
        val (chipBg, chipText) = when {
            statusLower.contains("entregado")                                -> CHIP_BG_DELIVERED to CHIP_TEXT_DELIVERED
            statusLower.contains("tránsito") || statusLower.contains("transito") ||
            statusLower.contains("reparto") || statusLower.contains("camino") -> CHIP_BG_TRANSIT to CHIP_TEXT_TRANSIT
            statusLower.contains("novedad") || statusLower.contains("problema") ||
            statusLower.contains("error") || statusLower.contains("devuelto")  -> CHIP_BG_PROBLEM to CHIP_TEXT_PROBLEM
            else                                                               -> CHIP_BG_DEFAULT to CHIP_TEXT_DEFAULT
        }
        val chipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = chipText
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.LEFT
        }
        val chipTextWidth = chipTextPaint.measureText(status)
        val chipPadH = 20f
        val chipPadV = 12f
        val chipLeft   = textX
        val chipTop    = avatarCy + 32f
        val chipRight  = chipLeft + chipTextWidth + chipPadH * 2
        val chipBottom = chipTop + chipTextPaint.textSize + chipPadV * 2

        val chipBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = chipBg }
        canvas.drawRoundRect(
            RectF(chipLeft, chipTop, chipRight, chipBottom),
            20f, 20f, chipBgPaint
        )
        canvas.drawText(
            status,
            chipLeft + chipPadH,
            chipBottom - chipPadV - chipTextPaint.descent(),
            chipTextPaint
        )

        // ── Último evento ────────────────────────────────────────────────────
        val lastEvent = swe.events.maxByOrNull { it.timestamp }
        if (lastEvent != null) {
            val dividerPaint = Paint().apply {
                color = DIVIDER_COLOR
                strokeWidth = 1f
            }
            val divY = chipBottom + 28f
            canvas.drawLine(textX, divY, CARD_WIDTH - PADDING, divY, dividerPaint)

            val eventTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = TEXT_SECONDARY
                textSize = 24f
                textAlign = Paint.Align.LEFT
            }
            val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                .format(Date(lastEvent.timestamp))
            val eventDesc = lastEvent.description.let {
                if (it.length > 55) it.take(52) + "…" else it
            }
            canvas.drawText(dateStr, textX, divY + 36f, eventTextPaint)
            canvas.drawText(eventDesc, textX, divY + 70f, eventTextPaint)
        }

        // ── Marca de agua / branding ─────────────────────────────────────────
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFADB8F5.toInt()   // Indigo80
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(
            "Brako Tracker",
            CARD_WIDTH - PADDING,
            CARD_HEIGHT - PADDING / 2,
            brandPaint
        )

        return bitmap
    }
}
