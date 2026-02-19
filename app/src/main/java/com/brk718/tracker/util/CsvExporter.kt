package com.brk718.tracker.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.brk718.tracker.data.local.ShipmentWithEvents
import com.brk718.tracker.data.repository.ShipmentRepository
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    private val filenameDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    /**
     * Exporta todos los envíos (activos + archivados) a un CSV y lo guarda en Descargas.
     * Devuelve el Uri del archivo creado, o null si falló.
     */
    fun exportToCsv(
        context: Context,
        shipments: List<ShipmentWithEvents>
    ): Uri? {
        val filename = "envios_${filenameDateFormat.format(Date())}.csv"
        val csvContent = buildCsv(shipments)

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+: usar MediaStore
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                resolver.openOutputStream(uri)?.use { it.write(csvContent.toByteArray(Charsets.UTF_8)) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                // Android 9 y anteriores: escribir en carpeta Descargas pública
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, filename)
                FileOutputStream(file).use { it.write(csvContent.toByteArray(Charsets.UTF_8)) }
                Uri.fromFile(file)
            }
        } catch (e: Exception) {
            android.util.Log.e("CsvExporter", "Error exportando CSV: ${e.message}")
            null
        }
    }

    private fun buildCsv(shipments: List<ShipmentWithEvents>): String {
        val sb = StringBuilder()
        // BOM para que Excel lo abra correctamente con acentos
        sb.append('\uFEFF')
        // Cabecera
        sb.appendLine("Título,Número de seguimiento,Transportista,Estado,Última actualización,Archivado,Último evento")
        // Filas
        for (item in shipments) {
            val s = item.shipment
            val lastEvent = item.events.maxByOrNull { it.timestamp }?.description ?: ""
            sb.appendLine(
                listOf(
                    s.title.csvEscape(),
                    s.trackingNumber.csvEscape(),
                    ShipmentRepository.displayName(s.carrier).csvEscape(),
                    s.status.csvEscape(),
                    dateFormat.format(Date(s.lastUpdate)).csvEscape(),
                    if (s.isArchived) "Sí" else "No",
                    lastEvent.csvEscape()
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    /** Escapa un campo CSV: encierra en comillas si contiene coma, comilla o salto de línea. */
    private fun String.csvEscape(): String {
        return if (contains(',') || contains('"') || contains('\n')) {
            "\"${replace("\"", "\"\"")}\""
        } else this
    }
}
