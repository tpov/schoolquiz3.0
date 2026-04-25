plugins {
    id("schoolquiz.kmp.library")
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.tpov.schoolquiz.shared.core.persistence"
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    sourceSets {
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.androidx.room.runtime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.androidx.sqlite.bundled)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
        androidInstrumentedTest.dependencies {
            implementation(libs.androidx.room.testing)
            implementation(libs.androidx.test.ext.junit)
            implementation(libs.androidx.test.runner)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspJvm", libs.androidx.room.compiler)
}


