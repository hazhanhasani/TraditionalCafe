plugins {
    id("com.android.application")
}

val keystorePath = System.getenv("TC_KEYSTORE_PATH")
val keystorePassword = System.getenv("TC_KEYSTORE_PASSWORD")
val keyAliasValue = System.getenv("TC_KEY_ALIAS")
val keyPasswordValue = System.getenv("TC_KEY_PASSWORD")

val releaseSigningReady = listOf(
    keystorePath,
    keystorePassword,
    keyAliasValue,
    keyPasswordValue
).all { !it.isNullOrBlank() }

android {
    namespace = "com.hazhanhasani.traditionalcafe"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hazhanhasani.traditionalcafe"
        minSdk = 26
        targetSdk = 35

        versionCode = (System.getenv("APP_VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("APP_VERSION_NAME") ?: "0.1.0"
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }

        getByName("release") {
            isMinifyEnabled = false
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
