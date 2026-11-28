import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (!localPropertiesFile.exists()) {
    throw GradleException(
        "Missing local.properties in the project root. Create it using local.properties.example " +
            "and add SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY."
    )
}
localPropertiesFile.inputStream().use { localProperties.load(it) }

fun requiredLocalProperty(name: String): String {
    return localProperties.getProperty(name)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: throw GradleException("Missing $name in the root local.properties file.")
}

val supabaseUrl = requiredLocalProperty("SUPABASE_URL")
val supabasePublishableKey = requiredLocalProperty("SUPABASE_PUBLISHABLE_KEY")

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.mahalaxmi.expiry"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.mahalaxmi.expiry"
        minSdk = 23
        targetSdk = 35
        versionCode = 8
        versionName = "2.5.2"
        buildConfigField("String", "SUPABASE_URL", supabaseUrl.asBuildConfigString())
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", supabasePublishableKey.asBuildConfigString())
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
