# Plan de Desarrollo: Parcel Tracker Android (Material 3)

Este plan describe la construcción de una aplicación de seguimiento de envíos nativa en Android utilizando Jetpack Compose y Material 3, inspirada en la funcionalidad de "Parcel" de iOS pero con estética moderna de Android.

## Fase 1: Configuración y Estructura Base
- [ ] **Dependencias**: Configurar `build.gradle` y `libs.versions.toml` con:
    - Navigation Compose
    - Room (Base de datos local)
    - Retrofit + OkHttp (Red)
    - Kotlin Serialization (JSON parsing)
    - Hilt (Inyección de dependencias) - *Opcional pero recomendado para escalabilidad, o Manual DI para simplicidad inicial.*
    - WorkManager (Tareas en segundo plano)
- [ ] **Estructura de Paquetes**: Organizar en `data`, `domain`, `ui` (MVVM).

## Fase 2: Capa de Datos (Persistencia y Red)
- [ ] **Entidades Room**:
    - `ShipmentEntity`: ID, tracking number, carrier, título, estado actual, fecha de actualización.
    - `TrackingEventEntity`: Relación 1:N con Shipment (fecha, descripción, ubicación).
- [ ] **DAO**: Métodos para insertar envíos, obtener lista (Flow), obtener detalle con eventos.
- [ ] **API Service**:
    - Definir interfaz `TrackingApi`.
    - Implementar un "Mock" o cliente real (ej. 17Track/AfterShip si se tiene API Key, o un scraper simulado/mock para desarrollo).
- [ ] **Repository**: `ShipmentRepository` para orquestar fuente local (Room) y remota (API).

## Fase 3: Lógica de Negocio y ViewModels
- [ ] **HomeViewModel**: Exponer `StateFlow<List<Shipment>>`. Lógica para borrar/archivar.
- [ ] **AddShipmentViewModel**: Validar entrada, detectar carrier (simulado o por regex), guardar en DB.
- [ ] **DetailViewModel**: Cargar datos del envío y sus eventos históricos.

## Fase 4: Integración de Fuentes (Email y Amazon)
- [ ] **Lectura de Correo (Gmail Integration)**:
    - Implementar Google Sign-In para obtener acceso al correo (scope `gmail.readonly`).
    - Servicio para escanear últimos correos buscando palabras clave ("tú pedido", "enviado", "tracking", "Amazon").
    - Parser con expresiones regulares para extraer números de seguimiento (UPS, DHL, FedEx, Correos).
- [ ] **Soporte Amazon**:
    - Parser específico para correos de confirmación de Amazon.
    - Extracción de enlaces de "Rastrear paquete" o números TBA (Amazon Logistics).
    - Estrategia de actualización: Consulta periódica vía API simulada o scraping ligero del enlace de seguimiento (nota: esto puede ser frágil).

## Fase 5: Interfaz de Usuario (Material 3)
- [ ] **Tema y Color**: Configurar paleta de colores dinámica (Dynamic Colors) y tipografía.
- [ ] **Navegación**: Configurar `NavHost` con rutas: `Home`, `Add`, `Detail`.
- [ ] **Pantalla Principal (Home)**:
    - Lista con `LazyColumn`.
    - `Card` para cada envío mostrando estado (colores semánticos: verde=entregado, azul=tránsito).
    - FAB grande para añadir.
- [ ] **Pantalla de Detalle**:
    - Encabezado con resumen.
    - Línea de tiempo vertical (Timeline) para los eventos.
- [ ] **Pantalla de Añadir**:
    - Campos de texto con validación.
    - Selector de carrier (o detección auto).

## Fase 5: Sincronización en Segundo Plano
- [ ] **WorkManager**: Crear `SyncWorker` que se ejecute cada X horas.
- [ ] **Lógica de Sync**: Iterar envíos activos -> Consultar API -> Actualizar DB -> Notificar si hay cambios.
- [ ] **Notificaciones**: Mostrar notificación local cuando cambie el estado.

## Fase 6: Refinamiento y "Polishing"
- [ ] **Iconos de Carrier**: Mapeo básico de logos/iconos.
- [ ] **Empty States**: Ilustraciones cuando no hay envíos.
- [ ] **Transiciones**: Animaciones de entrada/salida entre pantallas.
