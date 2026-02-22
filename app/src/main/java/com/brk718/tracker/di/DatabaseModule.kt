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
        // NOTA: No usar fallbackToDestructiveMigration() — borraría todos los envíos del usuario
        // Al cambiar el schema, incrementar version en TrackerDatabase y añadir una Migration aquí
        // Ejemplo: .addMigrations(MIGRATION_6_7)
        .build()
    }

    @Provides
    fun provideShipmentDao(database: TrackerDatabase): ShipmentDao {
        return database.shipmentDao()
    }
}
