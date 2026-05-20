plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.pisces312.milocal"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pisces312.milocal"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfigs.create("release") {
                val keystorePath = System.getenv("KEY_STORE_LOCATION")
                    ?: throw GradleException("KEY_STORE_LOCATION environment variable not set")
                storeFile = file(keystorePath)
                keyAlias = System.getenv("KEY_ALIAS")
                    ?: throw GradleException("KEY_ALIAS environment variable not set")
                storePassword = System.getenv("KEY_STORE_PASSWORD")
                    ?: throw GradleException("KEY_STORE_PASSWORD environment variable not set")
                keyPassword = System.getenv("KEY_PASSWORD")
                    ?: System.getenv("KEY_STORE_PASSWORD")
            }
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
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.room:room-migration:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Accompanist Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.37.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
