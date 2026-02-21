package com.brk718.tracker.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "shipments")
data class ShipmentEntity(
    @PrimaryKey val id: String, // UUID
    val trackingNumber: String,
    val carrier: String,
    val title: String,
    val status: String, // "En tránsito", "Entregado", etc.
    val lastUpdate: Long, // Timestamp
    val isArchived: Boolean = false,
    val estimatedDelivery: Long? = null, // Timestamp de entrega estimada (null si no disponible)
    val subCarrierName: String? = null,        // Carrier de última milla (ej. "PASAREX")
    val subCarrierTrackingId: String? = null   // ID de rastreo del carrier (ej. "AMZPSR021419193")
)

@Entity(
    tableName = "tracking_events",
    foreignKeys = [
        ForeignKey(
            entity = ShipmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["shipmentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["shipmentId"]),
        // location incluida en el índice único: permite mismo mensaje en distintas ciudades
        // y eventos sin ubicación con mismo mensaje pero diferente timestamp
        Index(value = ["shipmentId", "description", "timestamp", "location"], unique = true)
    ]
)
data class TrackingEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shipmentId: String,
    val timestamp: Long,
    val description: String,
    val location: String?,
    val status: String, // Estado en ese momento
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class ShipmentWithEvents(
    @androidx.room.Embedded val shipment: ShipmentEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "shipmentId"
    )
    val events: List<TrackingEventEntity>
)
