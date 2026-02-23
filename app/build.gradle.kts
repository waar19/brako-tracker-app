import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "com.brk718.tracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.brk718.tracker"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1-beta"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Leer API keys y signing config desde local.properties
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(localPropertiesFile.inputStream())
        }
        buildConfigField("String", "AFTERSHIP_API_KEY", "\"${localProperties["AFTERSHIP_API_KEY"] ?: ""}\"")
        buildConfigField("String", "GMAIL_CLIENT_ID", "\"${localProperties["GMAIL_CLIENT_ID"] ?: ""}\"")
        buildConfigField("String", "OUTLOOK_CLIENT_ID", "\"${localProperties["OUTLOOK_CLIENT_ID"] ?: ""}\"")
    }

    signingConfigs {
        create("release") {
            val localProperties = Properties()
            val localPropertiesFile = rootProject.file("local.properties")
            if (localPropertiesFile.exists()) {
                localProperties.load(localPropertiesFile.inputStream())
            }
            storeFile     = file(localProperties["RELEASE_STORE_FILE"]     ?: "brako-release.jks")
            storePassword = localProperties["RELEASE_STORE_PASSWORD"]      as String? ?: ""
            keyAlias      = localProperties["RELEASE_KEY_ALIAS"]           as String? ?: ""
            keyPassword   = localProperties["RELEASE_KEY_PASSWORD"]        as String? ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Room: exportar schemas a /app/schemas/ para poder escribir migraciones futuras
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.ui:ui-text-google-fonts")
    
    // Navigation & Icons
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Retrofit
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // DataStore (preferencias de usuario)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Splash Screen API (compat Android 6+)
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Google Mobile Ads (AdMob)
    implementation("com.google.android.gms:play-services-ads:23.3.0")

    // Google Play Billing
    implementation("com.android.billingclient:billing-ktx:7.0.0")

    // In-App Review (rating dialog)
    implementation("com.google.android.play:review-ktx:2.0.1")

    // Confetti animation
    implementation("nl.dionsegijn:konfetti-compose:2.0.4")

    // Microsoft Authentication Library (Outlook/Hotmail OAuth2 + Graph API)
    // MSAL 4.x usa OpenTelemetry en runtime. El BOM no puede resolverse como librería
    // en Android Gradle → se excluye el BOM, pero se añade la API directamente.
    implementation("com.microsoft.identity.client:msal:4.9.0") {
        exclude(group = "io.opentelemetry", module = "opentelemetry-bom")
    }
    // Clases de OpenTelemetry que MSAL necesita en runtime (SpanContext, etc.)
    implementation("io.opentelemetry:opentelemetry-api:1.18.0")

    // Play Services Auth (Gmail)
    implementation(libs.play.services.auth)
    implementation(libs.google.api.client.android)
    implementation(libs.google.api.services.gmail) {
        exclude(group = "org.apache.httpcomponents")
    }
    
    
    // HTML parsing
    implementation(libs.jsoup)

    // Maps (OpenStreetMap)
    implementation(libs.osmdroid.android)

    // Jetpack Glance (Widget — solo premium)
    implementation("androidx.glance:glance-appwidget:1.1.0")
    implementation("androidx.glance:glance-material3:1.1.0")

    // CameraX + ML Kit (escáner de código de barras en AddScreen)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.barcode)

    // Firebase (Crashlytics + Analytics + FCM)
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
    // FCM: infraestructura lista para push desde backend (token almacenado en DataStore)
    implementation("com.google.firebase:firebase-messaging-ktx")

    testImplementation(libs.junit)
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("app.cash.turbine:turbine:1.1.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
