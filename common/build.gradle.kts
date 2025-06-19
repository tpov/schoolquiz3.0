@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    id("android-library-convention") // Applies com.android.library, org.jetbrains.kotlin.android, ksp
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.parcelize)
    // detekt and ktlint are applied from the root project
}

android {
    namespace = "com.tpov.common"
    // compileSdk, minSdk, defaultConfig.testInstrumentationRunner are set by convention.
    // targetSdk is not set for libraries by convention.
    // consumerProguardFiles("consumer-rules.pro") is set by convention.

    // Specific packagingOptions for this module
    packagingOptions {
        resources {
            excludes += "kotlin/**"
            excludes += "META-INF/**" // This is quite broad, ensure it's not excluding needed META-INF files for dependencies
            excludes += "kotlin/internal/internal.kotlin_builtins"
            excludes += "kotlin/collections/collections.kotlin_builtins"
            excludes += "kotlin/reflect/reflect.kotlin_builtins"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true // Override convention plugin's default (false for libraries)
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.fragment.ktx)
    implementation(project(":log-api")) // Project dependency

    // Room dependencies (runtime, ktx, compiler via kapt)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    // Firebase (using -ktx version from app, assuming common might need it too)
    implementation(libs.firebase.functions.ktx) // Make sure this is the one intended, or use libs.firebase.functions

    // ML Kit
    implementation(libs.google.mlkit.translate)
    implementation(libs.google.mlkit.languageid)


    // Dagger 2 dependencies
    implementation(libs.dagger)
    kapt(libs.dagger.compiler)

    // Kotlin Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // AndroidX Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx) // Standardized version

    // Glide
    implementation(libs.bumptech.glide) // Standardized version
    kapt(libs.bumptech.glide.compiler)

    // AssistedInject for Dagger
    implementation(libs.squareup.assistedinject.annotations.dagger2)
    kapt(libs.squareup.assistedinject.processor.dagger2) // Standardized version

    implementation(libs.google.auth.library.oauth2.http)

    // Robolectric should only be a test dependency
    testImplementation(libs.robolectric)
}
