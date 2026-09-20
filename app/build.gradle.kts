plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.tripex.pose"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.tripex.pose"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // h3-android ships only these ABIs — keep the APK aligned so System.loadLibrary
        // always resolves libh3-java.so on physical ARM devices.
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    buildFeatures {
        compose = true
        // Needed for BuildConfig.DEBUG (TrackingService mock-location gate).
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            // Explicit: patched Uber H3 natives for arm32/arm64 (see src/main/jniLibs/).
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // AGP otherwise strips jar-embedded *.so (android-arm64/…) used by H3Core.newInstance.
            // We load via System.loadLibrary + jniLibs; keep pickFirst so merge never fails.
            pickFirsts += "**/android-arm64/libh3-java.so"
            pickFirsts += "**/android-arm/libh3-java.so"
        }
        jniLibs {
            // Extract .so to the filesystem — more reliable than in-APK mmap on some devices.
            useLegacyPackaging = true
            // Prefer :app/src/main/jniLibs (libm-patched) over the stock AAR copies.
            pickFirsts += "**/libh3-java.so"
            pickFirsts += "lib/arm64-v8a/libh3-java.so"
            pickFirsts += "lib/armeabi-v7a/libh3-java.so"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":ui"))
    implementation(project(":data"))
    implementation(project(":domain"))
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)

    implementation(libs.maplibre.android)

    // Direct app dependency so the Android AAR’s jni/ tree is always on the merge classpath.
    // Runtime load uses H3Core.newSystemInstance() + :app jniLibs (patched libm).
    implementation(libs.h3.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    testImplementation(libs.junit)
}
