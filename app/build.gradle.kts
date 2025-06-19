@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    id("android-application-convention") // Applies com.android.application, org.jetbrains.kotlin.android, com.google.devtools.ksp
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.parcelize) // Changed from "kotlin-parcelize" to the alias from TOML
    alias(libs.plugins.google.services)
    alias(libs.plugins.tpov.logger)
    // detekt and ktlint are applied from the root project
}

kapt {
    correctErrorTypes = true
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
        // arg("kapt.verbose", "true") // Usually not needed unless debugging kapt issues
    }
}

android {
    namespace = "com.tpov.schoolquiz"
    lint {
        baseline = file("lint-baseline.xml")
    }
    // compileSdk is set by convention plugin
    // minSdk, targetSdk are set by convention plugin

    sourceSets["androidTest"].assets.srcDirs("$projectDir/schemas")

    defaultConfig {
        applicationId = "com.tpov.schoolquiz"
        versionCode = 30018
        versionName = "3.0.18-rc"
        // testInstrumentationRunner is set by convention plugin
        // multiDexEnabled is set by convention plugin

        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }
    }

    // buildFeatures.viewBinding is set by convention plugin
    // packagingOptions are set by convention plugin
    // compileOptions and kotlinOptions are set by convention plugin
    // buildTypes.release.isMinifyEnabled is set by convention plugin (default false)
    // Proguard files for release are kept here as they are app-specific
    buildTypes {
        getByName("release") {
            // isMinifyEnabled = false // This is already the default in convention plugin
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(project(":userguide"))
    implementation(project(":common"))
    implementation(project(":shop"))
    implementation(project(":settings"))
    implementation(project(":network"))
    implementation(project(":log-api"))

    // Dependencies provided by android-application-convention are not repeated here
    // e.g. kotlin.stdlib, androidx.core.ktx, androidx.appcompat, lifecycle, room, dagger, gson, firebase-bom, etc.

    implementation(libs.google.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragmentktx)
    implementation(libs.androidx.navigation.uiktx)
    implementation(libs.androidx.lifecycle.livedata.ktx) // Specific version from app
    implementation(libs.androidx.lifecycle.viewmodel.ktx) // Specific version from app
    implementation(libs.androidx.preference.ktx)

    implementation(libs.android.billingclient)
    implementation(libs.play.services.ads)
    implementation(libs.androidx.gridlayout)

    implementation(libs.retrofit.adapter.rxjava2)
    implementation(libs.rxjava2.rxandroid)
    implementation(libs.rxjava2.rxjava)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.databinding.runtime)
    implementation(libs.google.mlkit.languageid.common)

    implementation(libs.google.mlkit.languageid)
    implementation(libs.philjay.mpandroidchart)
    implementation(libs.kotlin.parcelize.runtime) // Uses version from TOML (kotlin version)

    implementation(libs.squareup.picasso)
    implementation(libs.wasabeef.picasso.transformations)
    implementation(libs.bumptech.glide)
    kapt(libs.bumptech.glide.compiler)
    implementation(libs.firebase.appcheck.debug) // Specific firebase dep
    implementation(libs.firebase.functions.ktx) // Specific firebase dep (app uses -ktx)

    // These were test dependencies declared as implementation, moved to testImplementation/androidTestImplementation
    // implementation("androidx.test.ext:junit-ktx:1.2.1")
    // implementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(libs.androidx.test.ext.junitktx) //androidTestImplementation as it's for Android tests
    androidTestImplementation(libs.androidx.test.espresso.core) //androidTestImplementation

    kapt(libs.wasabeef.glide.transformations) // This was kapt in original, kept as kapt

    kapt(libs.androidx.room.compiler)
    kapt(libs.dagger.compiler)
    implementation(libs.squareup.assistedinject.annotations.dagger2)
    kapt(libs.squareup.assistedinject.processor.dagger2)

    implementation(libs.google.guava) // Be mindful of this dependency size

    androidTestImplementation(libs.androidx.arch.core.testing)

    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.robolectric)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
