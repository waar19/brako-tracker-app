package com.brk718.tracker.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
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
    val isArchived: Boolean = false
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
    ]
)
data class TrackingEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shipmentId: String,
    val timestamp: Long,
    val description: String,
    val location: String?,
    val status: String // Estado en ese momento
)

data class ShipmentWithEvents(
    @androidx.room.Embedded val shipment: ShipmentEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "shipmentId"
    )
    val events: List<TrackingEventEntity>
)
