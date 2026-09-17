import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.antigrav.bignotes"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.antigrav.bignotes"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.graphics:graphics-core:1.0.2")
    implementation("androidx.input:input-motionprediction:1.0.0-beta03")
    implementation("androidx.documentfile:documentfile:1.0.1")

    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Google ML Kit Digital Ink Recognition (offline handwriting recognition)
    implementation("com.google.mlkit:digital-ink-recognition:18.1.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.register("archiveDebugApk") {
    group = "archive"
    description = "Archives built debug APK into root archives/apks/ with timestamp, version and git hash."

    doLast {
        val buildDir = layout.buildDirectory.asFile.get()
        val apkFile = File(buildDir, "outputs/apk/debug/app-debug.apk")
        if (!apkFile.exists()) {
            println("APK not found at ${apkFile.absolutePath}")
            return@doLast
        }

        val archivesDir = File(rootDir, "archives/apks").apply { mkdirs() }
        val historyFile = File(rootDir, "archives/build_history.md")

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val gitHash = try {
            val stdout = ByteArrayOutputStream()
            exec {
                commandLine = listOf("git", "rev-parse", "--short", "HEAD")
                standardOutput = stdout
            }
            stdout.toString().trim()
        } catch (e: Exception) {
            "unknown"
        }

        val versionName = android.defaultConfig.versionName ?: "1.0.0"
        val versionCode = android.defaultConfig.versionCode ?: 1
        val archiveFileName = "BigNotes-v${versionName}-b${versionCode}-${timestamp}-${gitHash}-debug.apk"
        val destFile = File(archivesDir, archiveFileName)

        apkFile.copyTo(destFile, overwrite = true)
        val sizeMb = String.format(Locale.US, "%.2f MB", destFile.length() / (1024.0 * 1024.0))

        if (!historyFile.exists()) {
            historyFile.writeText(
                "# BigNotes APK Build History\n\n" +
                "| Date & Time | Version | Filename | Git Commit | File Size |\n" +
                "| :--- | :--- | :--- | :--- | :--- |\n"
            )
        }
        val dateFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val row = "| $dateFormatted | v${versionName} (b${versionCode}) | `$archiveFileName` | `$gitHash` | $sizeMb |\n"
        historyFile.appendText(row)

        println(" Archived APK to: ${destFile.absolutePath} ($sizeMb)")
        println(" Updated build history: ${historyFile.absolutePath}")
    }
}

afterEvaluate {
    tasks.findByName("assembleDebug")?.let { assembleTask ->
        tasks.findByName("archiveDebugApk")?.let { archiveTask ->
            archiveTask.dependsOn(assembleTask)
            assembleTask.finalizedBy(archiveTask)
        }
    }
}
