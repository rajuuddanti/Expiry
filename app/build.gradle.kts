import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

android {
    namespace = "com.mahalaxmi.expiry"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.mahalaxmi.expiry"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "0.2.0"
        buildConfigField("String", "SUPABASE_URL", "\"" + localProperties.getProperty("SUPABASE_URL", "https://tmdswprfiaiywzrwzucj.supabase.co") + "\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"" + localProperties.getProperty("SUPABASE_PUBLISHABLE_KEY", "sb_publishable_3Z-QGbnE36Tgo8Z8UB_dSw_9zs1b_Mu") + "\"")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
}
