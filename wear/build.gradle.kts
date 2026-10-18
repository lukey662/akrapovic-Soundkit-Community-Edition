import org.gradle.api.GradleException

val releaseSigningInputs = mapOf(
    "ANDROID_RELEASE_STORE_FILE" to providers.environmentVariable("ANDROID_RELEASE_STORE_FILE").orNull,
    "ANDROID_RELEASE_STORE_PASSWORD" to providers.environmentVariable("ANDROID_RELEASE_STORE_PASSWORD").orNull,
    "ANDROID_RELEASE_KEY_ALIAS" to providers.environmentVariable("ANDROID_RELEASE_KEY_ALIAS").orNull,
    "ANDROID_RELEASE_KEY_PASSWORD" to providers.environmentVariable("ANDROID_RELEASE_KEY_PASSWORD").orNull,
)
val missingReleaseSigningInputs = releaseSigningInputs
    .filterValues { it.isNullOrBlank() }
    .keys

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.akrapovic.soundkit.community.wear"
    compileSdk = 35

    defaultConfig {
        // The Wearable Data Layer only links apps with the same application id and signing key.
        applicationId = "com.akrapovic.soundkit.community"
        minSdk = 30
        targetSdk = 35
        // Unique per artifact on Play; the watch build is filtered by android.hardware.type.watch.
        versionCode = 1_000_007
        versionName = "0.3.4"
    }

    signingConfigs {
        getByName("debug")
        create("release") {
            // Values are supplied only by the release environment; never add them to source control.
            if (missingReleaseSigningInputs.isEmpty()) {
                storeFile = file(requireNotNull(releaseSigningInputs.getValue("ANDROID_RELEASE_STORE_FILE")))
                storePassword = requireNotNull(releaseSigningInputs.getValue("ANDROID_RELEASE_STORE_PASSWORD"))
                keyAlias = requireNotNull(releaseSigningInputs.getValue("ANDROID_RELEASE_KEY_ALIAS"))
                keyPassword = requireNotNull(releaseSigningInputs.getValue("ANDROID_RELEASE_KEY_PASSWORD"))
            }
        }
    }

    buildTypes {
        debug {
            // Matches the phone debug build so the Data Layer pairs debug with debug.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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
}

gradle.taskGraph.whenReady {
    val releaseArtifactRequested = allTasks.any { task ->
        task.project == project &&
            task.name in setOf("assembleRelease", "bundleRelease", "packageRelease")
    }
    if (!releaseArtifactRequested) return@whenReady

    if (missingReleaseSigningInputs.isNotEmpty()) {
        throw GradleException(
            "Release signing requires environment variables: " +
                missingReleaseSigningInputs.joinToString(", "),
        )
    }

    val keystore = file(requireNotNull(releaseSigningInputs.getValue("ANDROID_RELEASE_STORE_FILE")))
    if (!keystore.isFile) {
        throw GradleException(
            "ANDROID_RELEASE_STORE_FILE does not point to a readable keystore: ${keystore.absolutePath}",
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.material)
    implementation(libs.androidx.wear.remote.interactions)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.guava.android)
}
