plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.gocar"
    compileSdk = 34 // Nota: El SDK 36 es muy experimental para 2026, lo ideal es 34 o 35 estable.

    defaultConfig {
        applicationId = "com.example.gocar"
        // CAMBIO CLAVE: Android 11 es API 30.
        // Esto soluciona automáticamente el error de <adaptive-icon> (que pide mínimo 26).
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    // Mantenemos solo la versión más reciente de material
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.8.0")
    implementation("androidx.cardview:cardview:1.0.0")

    // Reemplaza la versión por la más reciente disponible
    implementation("com.mapbox.maps:android:11.0.0")

    implementation("com.google.android.gms:play-services-location:21.0.1")


}