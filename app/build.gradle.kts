import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}
val properties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    properties.load(localPropertiesFile.inputStream())
}

android {
    namespace = "io.github.guibecko.skydex"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "io.github.guibecko.skydex"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Where the app looks for the backend, baked in at compile time as BuildConfig.BASE_URL.
        //
        // `local.properties` wins when it defines API_BASE_URL (that file is git-ignored, so each
        // machine keeps its own address); this literal is the fallback for a fresh clone.
        //
        // It must be the host's LAN IP, never `localhost` — on a real device `localhost` is the
        // *phone*, so an app built that way can only reach the backend through an `adb reverse`
        // tunnel, and it fails with a connection error the moment the tunnel is missing. The
        // previous fallback was `http://<host>:8080`: a stale address on a subnet this
        // machine no longer uses, and the wrong port besides — 8080 is Keycloak, the SkyDex backend
        // is 3002 (`server.port` in the backend's application.properties).
        //
        // Being an IP, this rots when the network changes. If it stops working, put your own
        // address in `local.properties` rather than editing this line and committing it.
        val apiUrl = properties.getProperty("API_BASE_URL") ?: "\"http://10.0.2.2:3002\""
        buildConfigField("String", "BASE_URL", apiUrl)
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
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
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.compose.animation)
	implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.play.services.location)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
	implementation("androidx.compose.material:material-icons-extended")
    // Interceptor para ver os logs das requisições
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
    // Retrofit para requisições
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    // Gson para converter o JSON da sua API para classes Kotlin
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("io.coil-kt:coil-compose:2.6.0")
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
