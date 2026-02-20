package com.brk718.tracker.ui.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Receiver que registra el widget en el sistema.
 * Glance se encarga del ciclo de vida completo — solo hay que declararlo en el Manifest.
 */
class TrackerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TrackerWidget()
}
