import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Firebase project configuration is environment-owned. Keeping the plugin
// conditional lets a fresh clone run unit tests before its debug/release
// google-services.json files have been provisioned.
val hasGoogleServicesConfig = listOf(
    file("google-services.json"),
    file("src/debug/google-services.json"),
    file("src/release/google-services.json")
).any { it.exists() }
if (hasGoogleServicesConfig) {
    apply(plugin = "com.google.gms.google-services")
}

// The server API key is read from local.properties, which is gitignored, so the
// secret never lands in a commit. An absent key builds to an empty string: the
// app then sends no Authorization header, which the server still accepts while
// AUTH_ENFORCE is off. Once enforcement is on, a keyless build stops working.
val serverApiKey: String = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use { load(it) }
    }
}.getProperty("SERVER_API_KEY").orEmpty().trim()

android {
    namespace = "com.example.kakaomiddleware"
    compileSdk = 36

    buildFeatures {
        compose = true
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.kakaomiddleware"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "API_KEY", "\"$serverApiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "API_ENDPOINT", "\"https://kakao-agent-server-dun.vercel.app/api/v1/process-message\"")
        }
        debug {
            // Debug 빌드는 Settings에서 동적으로 설정된 서버 주소 사용
            // BuildConfig는 프로덕션 서버를 fallback으로만 사용
            buildConfigField("String", "API_ENDPOINT", "\"https://kakao-agent-server-dun.vercel.app/api/v1/process-message\"")
            applicationIdSuffix = ".debug"
        }
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

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    
    // Network dependencies
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // JSON serialization for chat context storage
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
