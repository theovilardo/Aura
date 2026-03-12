import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.android)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

fun escapeBuildConfig(value: String): String {
    return value.replace("\\", "\\\\").replace("\"", "\\\"")
}

android {
    namespace = "com.theveloper.aura"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.theveloper.aura"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val groqApiKey = localProperties.getProperty("groq.api.key")
            ?: System.getenv("GROQ_API_KEY")
            ?: ""
        val exchangeRateApiKey = localProperties.getProperty("exchangerate.api.key")
            ?: System.getenv("EXCHANGERATE_API_KEY")
            ?: "dummy_key_for_now"
        val supabaseUrl = localProperties.getProperty("supabase.url")
            ?: System.getenv("SUPABASE_URL")
            ?: "https://dummy.supabase.co"
        val supabaseKey = localProperties.getProperty("supabase.key")
            ?: System.getenv("SUPABASE_KEY")
            ?: "dummy_anon_key"

        buildConfigField("String", "GROQ_API_KEY", "\"${escapeBuildConfig(groqApiKey)}\"")
        buildConfigField("String", "GROQ_BASE_URL", "\"https://api.groq.com/openai/v1/\"")
        buildConfigField("String", "EXCHANGERATE_API_KEY", "\"${escapeBuildConfig(exchangeRateApiKey)}\"")
        buildConfigField("String", "SUPABASE_URL", "\"${escapeBuildConfig(supabaseUrl)}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"${escapeBuildConfig(supabaseKey)}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    kotlinOptions {
        jvmTarget = "11"
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
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.text.google.fonts)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Kotlin Serialization
    implementation(libs.kotlinx.serialization.json)

    // OkHttp & Retrofit
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization.converter)

    // ML Kit
    implementation(libs.mlkit.entity.extraction)
    implementation(libs.mlkit.language.id)

    // LiteRT-LM
    runtimeOnly(libs.litertlm.android)
    runtimeOnly(libs.litert.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.work.testing)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
