plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
}

android {
    namespace = "com.tpov.common"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        targetSdk = 34

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    packagingOptions {
        resources {
            excludes += "kotlin/**"
            excludes += "META-INF/**"
            excludes += "kotlin/internal/internal.kotlin_builtins"
            excludes += "kotlin/collections/collections.kotlin_builtins"
            excludes += "kotlin/reflect/reflect.kotlin_builtins"
        }
    }

    buildTypes {

        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation ("androidx.fragment:fragment-ktx:1.6.2")
    implementation(project(":log-api"))
    // Room dependencies
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("com.google.firebase:firebase-functions-ktx:21.0.0")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:language-id:17.0.6")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    // Dagger 2 dependencies
    implementation("com.google.dagger:dagger:2.48.1")
    kapt("com.google.dagger:dagger-compiler:2.48.1")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // AndroidX Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1")

    // Glide
    implementation("com.github.bumptech.glide:glide:4.15.1")
    kapt("com.github.bumptech.glide:compiler:4.15.1")

    // AssistedInject for Dagger
    implementation("com.squareup.inject:assisted-inject-annotations-dagger2:0.8.1")
    kapt("com.squareup.inject:assisted-inject-processor-dagger2:0.6.0")

    implementation ("com.google.auth:google-auth-library-oauth2-http:1.23.0")
    implementation ("org.robolectric:robolectric:4.10.3")
    testImplementation ("org.robolectric:robolectric:4.10.3")
}
