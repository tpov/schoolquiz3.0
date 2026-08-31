plugins {
    id("schoolquiz.kmp.library")
}

android {
    namespace = "com.tpov.schoolquiz.shared.core.analytics"
}

// Pure Kotlin by design: no Koin, no Firebase, no Android. Features depend on this module;
// the backend that actually delivers events lives in platform/firebase and is bound by DI.
