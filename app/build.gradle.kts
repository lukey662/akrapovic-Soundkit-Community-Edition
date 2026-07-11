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
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
    alias(libs.plugins.paparazzi)
}

android {
    namespace = "com.akrapovic.soundkit.community"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.akrapovic.soundkit.community"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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
            signingConfig = signingConfigs.getByName("debug")
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
        freeCompilerArgs += listOf(
            "-Xjsr305=strict",
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
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

kapt {
    correctErrorTypes = true
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    dependsOn("testDebugUnitTest")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.car.app)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    implementation(libs.timber)

    kapt(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

