import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(keystorePropertiesFile.inputStream())
    }
}

val releaseKeystoreFile: java.io.File? = run {
    if (!keystorePropertiesFile.exists()) return@run null
    val path = keystoreProperties.getProperty("storeFile", "").trim()
    if (path.isEmpty()) return@run null
    rootProject.file(path).takeIf { it.isFile }
}

android {
    namespace = "com.zeno.dialer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.zeno.dialer"
        minSdk = 34
        targetSdk = 34
        versionCode = 103
        versionName = "1.0.3"
    }

    signingConfigs {
        if (releaseKeystoreFile != null) {
            create("upload") {
                storeFile = releaseKeystoreFile
                storePassword = keystoreProperties.getProperty("storePassword", "")
                keyAlias = keystoreProperties.getProperty("keyAlias", "")
                keyPassword = keystoreProperties.getProperty("keyPassword", "")
            }
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
            // Use upload keystore when keystore.properties + store file exist; otherwise debug
            // keys (still a minified release APK — replace with upload keystore for Play Store).
            signingConfig = signingConfigs.findByName("upload")
                ?: signingConfigs.getByName("debug")
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
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.05.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.coil-kt:coil-compose:2.6.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
