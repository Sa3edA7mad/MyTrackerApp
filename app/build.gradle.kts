import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.mytrackerapp"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.mytrackerapp"
        // Plan v2: lowered from the template's 37 so the app installs on real devices.
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    // MigrationTestHelper reads the exported schema JSONs as an androidTest asset.
    sourceSets {
        getByName("androidTest") {
            assets.srcDirs("$projectDir/schemas")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// androidx.savedstate (pulled in transitively via Compose/activity) declares kotlinx
// serialization strictly at 1.7.3, which shadows the newer version androidx.room:room-migration
// (used by MigrationTestHelper) actually needs — its precompiled bundle-parsing serializers
// were built against a newer kotlinx.serialization.internal.GeneratedSerializer that added an
// abstract method, so resolving down to 1.7.3 fails at runtime with AbstractMethodError rather
// than at build time. Forcing the newer version for androidTest configs only fixes
// MigrationTestHelper without touching the app's own runtime classpath.
configurations.matching { it.name.contains("AndroidTest") }.configureEach {
    resolutionStrategy {
        force(
            "org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1",
            "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.8.1",
            "org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1",
            "org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.8.1"
        )
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // --- MyTrackerApp ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.kotlinx.serialization.core)
    androidTestImplementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
