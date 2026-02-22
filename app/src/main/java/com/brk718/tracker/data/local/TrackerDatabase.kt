package com.brk718.tracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ShipmentEntity::class, TrackingEventEntity::class],
    version = 6,
    exportSchema = true   // Exporta el schema a /schemas/ para poder escribir migraciones futuras
)
abstract class TrackerDatabase : RoomDatabase() {
    abstract fun shipmentDao(): ShipmentDao
}
