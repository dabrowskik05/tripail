import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.tripex.pose.data"
    compileSdk = libs.versions.compileSdk.get().toInt()

    val localProperties = Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        // Keep in sync with :app versionName — used for Nominatim User-Agent.
        buildConfigField("String", "VERSION_NAME", "\"0.1.0\"")
        buildConfigField(
            "String",
            "MAPTILER_API_KEY",
            "\"${localProperties.getProperty("MAPTILER_API_KEY", "")}\"",
        )
        // h3-android ships only arm32/arm64 JNI; match that set so the linker
        // never looks for an ABI the AAR does not contain.
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        jniLibs {
            // Prefer the patched libh3-java.so if a Maven AAR ever reappears on the classpath.
            pickFirsts += "**/libh3-java.so"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    jvmToolchain(17)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    // Maven `com.uber:h3-android:4.4.0` is broken on device: missing libm.so in DT_NEEDED
    // (uber/h3-java#206; fix merged in PR #211, not released yet). We vendor:
    //   - libs/h3-android-4.4.0-classes.jar  (Java API from the official AAR)
    //   - src/main/jniLibs/*/libh3-java.so   (same natives + patchelf --add-needed libm.so)
    // Flip back to libs.h3.android when Uber publishes a fixed release.
    // AGP forbids local .aar deps in library modules, hence jar + jniLibs instead of a local AAR.
    implementation(files("libs/h3-android-4.4.0-classes.jar"))
    implementation(libs.play.services.location)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.h3.jvm)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.core)
}
