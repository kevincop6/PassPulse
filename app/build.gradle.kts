import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val buildNumberFile = rootProject.file("build-number.properties")
fun currentBuildNumber(): Int {
    if (!buildNumberFile.exists()) return 1
    return Properties().apply { buildNumberFile.inputStream().use { load(it) } }
        .getProperty("buildNumber", "1").toIntOrNull()?.coerceAtLeast(1) ?: 1
}

android {
    namespace = "com.ulpro.passpulse"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.ulpro.passpulse"
        minSdk = 31
        targetSdk = 36
        versionCode = currentBuildNumber()
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        viewBinding = true
    }
}

// Nombra el APK con la versión de la app y el número de compilación.
tasks.register("renamePassPulseDebugApk") {
    doLast {
        val outputDir = layout.buildDirectory.dir("outputs/apk/debug").get().asFile
        val generatedApk = outputDir.resolve("app-debug.apk")
        val version = android.defaultConfig.versionName.orEmpty().substringBefore('.').ifEmpty { "1" }
        val buildNumber = android.defaultConfig.versionCode ?: 1
        val namedApk = outputDir.resolve("PassPulse-v${version}(${buildNumber}).apk")
        if (generatedApk.exists()) {
            generatedApk.copyTo(namedApk, overwrite = true)
            Properties().apply { setProperty("buildNumber", (buildNumber + 1).toString()) }
                .also { properties -> buildNumberFile.outputStream().use { properties.store(it, "PassPulse build counter") } }
        }
    }
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy("renamePassPulseDebugApk")
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
