plugins {
    id("schoolquiz.kmp.library")
}

android {
    namespace = "com.tpov.schoolquiz.shared.feature.lesson_runner.data"
}

kotlin {
    sourceSets {
        val jvmAndAndroidMain by creating {
            dependsOn(commonMain.get())
        }
        jvmMain.get().dependsOn(jvmAndAndroidMain)
        androidMain.get().dependsOn(jvmAndAndroidMain)

        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(project(":shared:feature:lesson-runner:domain"))
            implementation(project(":shared:feature:lesson:domain"))
            implementation(project(":shared:core:question-schema"))
            implementation(project(":shared:core:persistence"))
        }
        androidMain.dependencies {
            implementation(libs.koin.core)
            implementation(project(":shared:feature:question:domain"))
            implementation(project(":shared:feature:app-shell:domain"))
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.junit4)
        }
    }
}
