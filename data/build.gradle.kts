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
        // Keep in sync with tools/atlas/build_boundaries.sh BOUNDARIES_VERSION.
        buildConfigField("int", "BOUNDARIES_VERSION", "4")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

/**
 * Robolectric reads `src/test/assets`, but the atlas that ships is the one under `:app`.
 * Keeping a hand-copied duplicate here let the fixture drift a whole atlas version behind the
 * app — the tests kept passing against data the app no longer used. Syncing it before every test
 * run makes that impossible.
 */
val syncAtlasTestFixture by tasks.registering(Copy::class) {
    from(rootProject.file("app/src/main/assets/atlas")) {
        include("continents.geojson")
    }
    into(layout.projectDirectory.dir("src/test/assets/atlas"))
}

// Everything that reads `src/test/assets` has to be ordered after the sync, not just the asset
// merge: lint builds its own model of the same directory, and running lint in the same
// invocation as the tests made Gradle refuse the undeclared dependency outright.
tasks.matching {
    (it.name.startsWith("merge") && it.name.endsWith("UnitTestAssets")) ||
        it.name.contains("LintModel") ||
        it.name.startsWith("lintAnalyze")
}.configureEach { dependsOn(syncAtlasTestFixture) }

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

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    // Official Android AAR (JNI under jni/<abi>/). Patched arm natives that fix
    // missing libm.so (uber/h3-java#206) live in :app/src/main/jniLibs and win via pickFirst.
    implementation(libs.h3.android)
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
