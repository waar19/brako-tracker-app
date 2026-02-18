package com.brk718.tracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ShipmentEntity::class, TrackingEventEntity::class],
    version = 2,
    exportSchema = false
)
abstract class TrackerDatabase : RoomDatabase() {
    abstract fun shipmentDao(): ShipmentDao
}
