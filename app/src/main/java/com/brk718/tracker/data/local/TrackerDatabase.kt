package com.brk718.tracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ShipmentEntity::class, TrackingEventEntity::class],
    version = 8,
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

        /** v6 → v7: se añadieron isMuted y reminderSent a ShipmentEntity */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shipments ADD COLUMN isMuted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE shipments ADD COLUMN reminderSent INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v7 → v8: corrección de hash inconsistente en dispositivos con build intermedio de v7.
         * Añade isMuted/reminderSent condicionalmente (por si MIGRATION_6_7 no corrió en ese dispositivo).
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val cursor = db.query("PRAGMA table_info(shipments)")
                val colNames = mutableSetOf<String>()
                while (cursor.moveToNext()) {
                    colNames.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
                cursor.close()
                if ("isMuted" !in colNames) {
                    db.execSQL("ALTER TABLE shipments ADD COLUMN isMuted INTEGER NOT NULL DEFAULT 0")
                }
                if ("reminderSent" !in colNames) {
                    db.execSQL("ALTER TABLE shipments ADD COLUMN reminderSent INTEGER NOT NULL DEFAULT 0")
                }
            }
        }
    }
}
