plugins {
    id("schoolquiz.kmp.library.agp9")
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.androidx.room.runtime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.androidx.sqlite.bundled)
            implementation(project(":shared:core:question-schema"))
            implementation(project(":shared:core:leaderboard"))
            api(project(":shared:core:outbox"))
            implementation(project(":shared:core:sync"))
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
        androidDeviceTest.dependencies {
            implementation(libs.androidx.room.testing)
            implementation(libs.androidx.test.ext.junit)
            implementation(libs.androidx.test.runner)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            // Room runs on the JVM through the bundled SQLite driver, so DAO behaviour that
            // depends on real SQL semantics (foreign keys, cascades) can be covered without a device.
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.kotlinx.coroutines.test)
        }
    }

    sourceSets.getByName("androidDeviceTest").kotlin.srcDir("src/androidInstrumentedTest/kotlin")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspJvm", libs.androidx.room.compiler)
}

androidComponents {
    onVariants { variant ->
        variant.deviceTests.values.forEach { deviceTest ->
            deviceTest.sources.assets?.addStaticSourceDirectory("$projectDir/schemas")
        }
    }
}
