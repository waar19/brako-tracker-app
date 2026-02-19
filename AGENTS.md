# Tracker System Architecture & Implementation Guide
This document provides a comprehensive overview of the Tracker application's architecture, data sources, and implementation details. It is designed to help future developers and AI agents understand the system's logic and extend its functionality.

1. System Overview
Tracker is an Android application (Kotlin + Jetpack Compose) designed to track shipments from various sources, with a heavy focus on Amazon orders and Colombian carriers. It aggregates tracking information from:

Gmail Integration: Automatically finding tracking numbers in emails.
Amazon Scraping: Extracting detailed status and event history for Amazon orders (including 111- order IDs).
AfterShip API: Tracking standard carrier shipments (FedEx, DHL, Coordinadora, Servientrega, etc.).
Manual Entry: User-inputted tracking numbers.
2. Core Architecture
The app follows a clean architecture pattern (Data -> Domain/Repo -> UI) using Hilt for dependency injection.

Data Flow
Input (Gmail/Manual) →
ShipmentRepository
 → DataSource (API/Scraper) → Room Database →
ViewModel
 → UI (Compose)

Key Modules
A. Amazon Tracking (Hybrid Approach)
The system uses a two-tiered approach to track Amazon packages:

Public API (
AmazonTrackingService
): used for standard tracking IDs like TBA..., AMZ....
Logic: Hits https://track.amazon.com/api/tracker/<id>  with a mobile User-Agent.
Parses: JSON response for status, estimation, and events.
Authenticated Scraper (
AmazonScraper
): used for Amazon Order IDs (111-...) which require login.
Auth: Uses 
AmazonAuthScreen
 (WebView) to log the user in and capture session cookies via 
AmazonSessionManager
.
Scraping:
Fetches order details page (/gp/your-account/order-details).
Finds the "Track Package" link (handles English "Track package", Spanish "Rastrear paquete", etc.).
Parses the tracking page HTML using Jsoup to extract:
Status: Main status header or progress bar text.
Arrival Date: Expected delivery.
Event History: Iterates through tracking-event-row elements to build a full timeline.
Location: Extracts location for mapping.
Date Parsing: 
ShipmentRepository
 contains robust logic to parse date strings (e.g., "Lunes, 20 de mayo") into timestamps, handling Spanish/English locales and inferring the year.
B. Gmail Integration (
GmailService
)
OAuth 2.0: Authenticates with Google to read emails with https://www.googleapis.com/auth/gmail.readonly  scope.
Filtering: Fetches emails from "Amazon.com", "Coordinadora", "Servientrega", "MercadoLibre", etc.
Parsing (EmailParser):
Uses Regex to extract tracking numbers.
Heuristics: Ignores common false positives (order numbers that look like tracking numbers).
Amazon Specifics: Extracts packageId from "Track Package" URLs if the text doesn't contain a clear tracking number.
C. Carrier Handling & AfterShip
ShipmentRepository
 acts as the central orchestrator.
Carrier Mapping: Maps local carrier names to AfterShip slugs (e.g., "Servientrega" -> servientrega, "Deprisa" -> deprisa).
Fallback Logic:
Amazon Check: If ID matches Amazon patterns (TBA, 111-), use 
AmazonTrackingService
.
Carrier Detection: If carrier is known, use AfterShip API with specific slug.
Auto-Detect: implementation tries to auto-detect carrier via AfterShip API.
D. Geocoding & Mapping
GeocodingService
: Converts location strings (e.g., "Miami, FL") to Latitude/Longitude using Nominatim (OpenStreetMap).
UI: Displays the shipment path on a map using Osmdroid.
3. Key Files & Responsibilities
File	Responsibility
ShipmentRepository.kt
Central logic. Decides whether to use AfterShip or Amazon scraper. Handles date parsing and DB updates.
AmazonScraper.kt
Jsoup-based HTML parser. Handles multi-language selectors and mobile Amazon layouts.
AmazonSessionManager.kt
Manages authentication cookies (x-main, at-main, etc.) using SharedPreferences.
AmazonTrackingService.kt
API client for track.amazon.com and facade for the Scraper.
GmailService.kt
Fetches relevant emails using Gmail API.
EmailParser.kt
Extracts tracking numbers/IDs from email bodies and subjects.
DetailScreen.kt
UI for shipment details, map, and event timeline.
4. Known Edge Cases & Handling
Amazon "PASAREX":
Some Amazon logistics partners in LatAm (like Pasarex) show up as "Enviado con PASAREX".
Handling: The scraper looks for specific container classes (transport-status-header) and ensures it finds the underlying "Rastrear" link even if the text varies.
Spanish Dates:
Amazon often displays dates like "18 de febrero" (no year).
Handling: ShipmentRepository.parseAmazonDate uses SimpleDateFormat with Spanish locale and infers the year based on current system time (handling year boundaries).
Cookie Expiry:
If scraping fails with a redirect to Sign-In, 
AmazonTrackingService
 catches it, clears the session, and sets the status to LOGIN_REQUIRED. The UI then prompts the user to re-connect.
5. Future AI Agent Context
When extending this system, keep in mind:

Scraper Fragility: The Amazon scraper depends on HTML structure. If Amazon changes class names, 
AmazonScraper.kt
 selectors will need updates. Always check doc.select(...) logic first.
User-Agent: We use a Mobile User-Agent (Linux; Android 10...) to get a simpler HTML structure and match the WebView's behavior.
Privacy: We only store session cookies locally. Credentials are never accessed directly; we intercept the login success state.
