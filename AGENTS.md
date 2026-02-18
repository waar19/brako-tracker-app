# Lineamientos de Diseño y Arquitectura - Parcel Tracker

Este documento define la arquitectura, estilo y reglas de desarrollo para la aplicación "Parcel Tracker".

## 1. Visión del Producto
Una aplicación Android nativa, moderna y minimalista para el seguimiento de envíos.
- **Inspiración**: "Parcel" de iOS, pero adaptado a Material 3.
- **Filosofía**: "Añadir y olvidar". La app se encarga de monitorizar y avisar.

## 2. Stack Tecnológico
- **Lenguaje**: Kotlin 100%.
- **UI**: Jetpack Compose + Material 3.
- **Arquitectura**: MVVM (Model-View-ViewModel) + Clean Architecture simplificada.
- **Inyección de Dependencias**: Hilt.
- **Persistencia**: Room (SQLite).
- **Red**: Retrofit + OkHttp.
- **Segundo Plano**: WorkManager.

## 3. Estructura del Proyecto
```
com.brk718.tracker
├── data                # Capa de Datos (Fuentes de verdad)
│   ├── local           # Base de datos (Room Entities, DAO)
│   ├── remote          # API (Retrofit, DTOs)
│   └── repository      # Orquestador de datos (Repository Pattern)
├── domain              # Lógica de Negocio Pura (Opcional en MVP)
│   └── EmailParser.kt  # Lógica de parsing de correos
├── di                  # Módulos Hilt (Network, Database)
├── ui                  # Capa de Presentación
│   ├── home            # Pantalla principal (Lista)
│   ├── detail          # Detalle del envío (Timeline)
│   ├── add             # Añadir nuevo envío
│   ├── theme           # Tema Material 3 (Colores, Tipografía)
│   └── App.kt          # Navegación (NavHost)
└── workers             # Tareas en segundo plano (SyncWorker)
```

## 4. Convenciones de Código

### UI (Compose)
- **Estado**: Cada pantalla tiene un `UiState` (sealed interface) que define sus estados: `Loading`, `Success`, `Error`.
- **Eventos**: Las acciones del usuario (clicks) se pasan como lambdas hacia arriba (`onClick: () -> Unit`).
- **Preview**: Cada componente visual complejo debe tener su `@Preview`.

### Naming
- **ViewModels**: `NombrePantallaViewModel` (ej. `HomeViewModel`).
- **Pantallas**: `NombreScreen` (ej. `HomeScreen`).
- **Entidades DB**: `NombreEntity` (ej. `ShipmentEntity`).

### Git Flow
- Ramas por feature (`feature/add-shipment`).
- Commits semánticos (`feat: add shipment logic`, `fix: crash on rotation`).

## 5. Integraciones Externas
- **APIs de Tracking**: Se utiliza una interfaz `TrackingApi` agnóstica. En el futuro se conectará a servicios reales (AfterShip, 17Track).
- **Gmail**: Se utiliza la API de Gmail (scope `readonly`) para buscar correos de pedidos.
- **Amazon**: Parser específico para correos de confirmación de Amazon.

## 6. Futuras Mejoras (Roadmap)
- [ ] Soporte real de OAuth para Gmail.
- [ ] Widgets de escritorio.
- [ ] Notificaciones Push (FCM).
- [ ] Modo oscuro/claro automático.
