plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val ciRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.personal.dsl224manager"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.personal.dsl224manager"
        minSdk = 24
        targetSdk = 34
        versionCode = 100 + ciRunNumber
        versionName = "1.1.$ciRunNumber"
    }

    val keystoreFile = rootProject.file("app/keystore/release.jks")
    val hasKeystore = keystoreFile.exists()
    val releaseStorePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD") ?: ""
    val releaseKeyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "dsl224_release_key"
    val releaseKeyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: ""

    signingConfigs {
        if (hasKeystore && releaseStorePassword.isNotEmpty()) {
            create("release") {
                storeFile = keystoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            if (hasKeystore && releaseStorePassword.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            isMinifyEnabled = false
            if (hasKeystore && releaseStorePassword.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.04.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // OkHttp & Coroutines
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
}
