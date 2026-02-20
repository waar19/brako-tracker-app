package com.brk718.tracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ShipmentDao {
    @Transaction
    @Query("SELECT * FROM shipments WHERE isArchived = 0 ORDER BY lastUpdate DESC")
    fun getAllActiveShipments(): Flow<List<ShipmentWithEvents>>

    @Transaction
    @Query("SELECT * FROM shipments WHERE isArchived = 1 ORDER BY lastUpdate DESC")
    fun getAllArchivedShipments(): Flow<List<ShipmentWithEvents>>

    @Query("UPDATE shipments SET isArchived = 0 WHERE id = :id")
    suspend fun unarchiveShipment(id: String)

    @Transaction
    @Query("SELECT * FROM shipments WHERE id = :id")
    fun getShipmentById(id: String): Flow<ShipmentWithEvents?>

    // Eventos ordenados por timestamp descendente (más reciente primero) para un envío
    @Query("SELECT * FROM tracking_events WHERE shipmentId = :shipmentId ORDER BY timestamp DESC")
    fun getEventsByShipment(shipmentId: String): Flow<List<TrackingEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShipment(shipment: ShipmentEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvents(events: List<TrackingEventEntity>)

    @Query("DELETE FROM tracking_events WHERE shipmentId = :shipmentId")
    suspend fun clearEventsForShipment(shipmentId: String)

    @Query("UPDATE shipments SET isArchived = 1 WHERE id = :id")
    suspend fun archiveShipment(id: String)
    
    @Query("DELETE FROM shipments WHERE id = :id")
    suspend fun deleteShipment(id: String)

    @Query("UPDATE shipments SET title = :newTitle WHERE id = :id")
    suspend fun updateTitle(id: String, newTitle: String)

    @Query("SELECT COUNT(*) FROM shipments")
    suspend fun countAllShipments(): Int

    @Query("SELECT COUNT(*) FROM shipments WHERE status = 'Entregado'")
    suspend fun countDeliveredShipments(): Int

    // ── Stats queries ──────────────────────────────────────────────────────

    /** Todos los envíos (activos + archivados) con sus eventos, para calcular estadísticas. */
    @Transaction
    @Query("SELECT * FROM shipments ORDER BY lastUpdate DESC")
    fun getAllShipmentsWithEvents(): Flow<List<ShipmentWithEvents>>

    /** Conteo de envíos agrupados por transportista (todos los envíos). */
    @Query("SELECT carrier, COUNT(*) as count FROM shipments GROUP BY carrier ORDER BY count DESC")
    suspend fun getCarrierCounts(): List<CarrierCount>
}

/** Resultado de la query de conteo por transportista. */
data class CarrierCount(val carrier: String, val count: Int)
