plugins {
    id("schoolquiz.kmp.library")
}

android {
    namespace = "com.tpov.schoolquiz.shared.core.sync"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(project(":shared:core:catalog:domain"))
            implementation(project(":shared:feature:quest:domain"))
            implementation(project(":shared:feature:section:domain"))
            implementation(project(":shared:feature:theme:domain"))
            implementation(project(":shared:feature:lesson:domain"))
            implementation(project(":shared:feature:question:domain"))
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
