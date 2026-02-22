package com.brk718.tracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ShipmentEntity::class, TrackingEventEntity::class],
    version = 6,
    exportSchema = true   // Exporta el schema a /schemas/ para poder escribir migraciones futuras
)
abstract class TrackerDatabase : RoomDatabase() {
    abstract fun shipmentDao(): ShipmentDao

    companion object {
        /** v4 → v5: se añadió estimatedDelivery (Long?) a ShipmentEntity */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shipments ADD COLUMN estimatedDelivery INTEGER")
            }
        }

        /** v5 → v6: se añadieron subCarrierName (String?) y subCarrierTrackingId (String?) a ShipmentEntity */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shipments ADD COLUMN subCarrierName TEXT")
                db.execSQL("ALTER TABLE shipments ADD COLUMN subCarrierTrackingId TEXT")
            }
        }
    }
}
