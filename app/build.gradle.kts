import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing: GitHub secrets win; otherwise the bundled keystore keeps updates installable.
val envKeystore = System.getenv("ITUBE_KEYSTORE_FILE")
val keystoreFile = if (!envKeystore.isNullOrBlank()) file(envKeystore) else rootProject.file("keystore/itube-release.jks")
val keystorePass = System.getenv("ITUBE_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() } ?: "itube-release-2026"
val keyAliasName = System.getenv("ITUBE_KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "itube"
val keyPass = System.getenv("ITUBE_KEY_PASSWORD")?.takeIf { it.isNotBlank() } ?: keystorePass
// GitHub Actions run number, or Codemagic build number offset past the GitHub builds.
val ciVersionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
    ?: System.getenv("BUILD_NUMBER")?.toIntOrNull()?.let { it + 100 }
    ?: 1

android {
    namespace = "com.gr4nt.itube"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.gr4nt.itube"
        minSdk = 26
        targetSdk = 36
        versionCode = ciVersionCode
        versionName = "1.0.$ciVersionCode"
    }

    signingConfigs {
        create("release") {
            storeFile = keystoreFile
            storePassword = keystorePass
            keyAlias = keyAliasName
            keyPassword = keyPass
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/*.version",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
            "META-INF/DEPENDENCIES",
            "META-INF/INDEX.LIST",
            "META-INF/**/previous-compilation-data.bin",
        )
    }
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=dev.chrisbanes.haze.ExperimentalHazeApi",
            "-opt-in=androidx.media3.common.util.UnstableApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
        )
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.dash)
    implementation(libs.media3.hls)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)

    implementation(libs.haze)
    implementation(libs.haze.glass)
    implementation(libs.haze.blur)

    implementation(libs.coil.compose)
    implementation(libs.coil.okhttp)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.work.runtime)
    implementation(libs.webkit)

    implementation(libs.newpipe.extractor)
}
