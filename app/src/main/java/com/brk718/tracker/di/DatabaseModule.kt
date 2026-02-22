package com.brk718.tracker.di

import android.content.Context
import androidx.room.Room
import com.brk718.tracker.data.local.ShipmentDao
import com.brk718.tracker.data.local.TrackerDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TrackerDatabase {
        return Room.databaseBuilder(
            context,
            TrackerDatabase::class.java,
            "tracker_db"
        )
        // Migraciones definidas — preservan los datos del usuario al actualizar la app
        .addMigrations(
            TrackerDatabase.MIGRATION_4_5,  // estimatedDelivery
            TrackerDatabase.MIGRATION_5_6,  // subCarrierName + subCarrierTrackingId
            TrackerDatabase.MIGRATION_6_7,  // isMuted + reminderSent
            TrackerDatabase.MIGRATION_7_8   // fix hash inconsistente (build intermedio de v7)
        )
        // Fallback de seguridad para versiones < 4 (beta antigua) — reinicia la DB si no hay migración
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideShipmentDao(database: TrackerDatabase): ShipmentDao {
        return database.shipmentDao()
    }
}
