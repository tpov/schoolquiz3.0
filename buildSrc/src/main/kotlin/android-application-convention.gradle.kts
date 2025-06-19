import com.android.build.gradle.AppExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.android")
            pluginManager.apply("com.google.devtools.ksp") // Apply KSP by default for apps
            // Potentially apply google-services if all apps use it.
            // pluginManager.apply("com.google.gms.google-services")


            extensions.configure<AppExtension> {
                compileSdkVersion(34) // Consider moving to a version catalog or ext property

                defaultConfig {
                    minSdk = 26
                    targetSdk = 34
                    versionCode = 1 // Default, should be overridden in app module
                    versionName = "1.0" // Default, should be overridden in app module
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    multiDexEnabled = true
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }

                kotlinOptions {
                    jvmTarget = "17"
                }

                packagingOptions {
                    resources.excludes.add("META-INF/INDEX.LIST")
                    resources.excludes.add("META-INF/DEPENDENCIES")
                }

                buildFeatures.apply {
                    viewBinding = true
                    // buildConfig = true // Enable if you need BuildConfig fields
                }

                // Default release build type (apps usually have this)
                buildTypes {
                    getByName("release") {
                        isMinifyEnabled = false // Default, can be overridden
                        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                    }
                }
            }

            // Accessing libs from version catalog
            val libs = extensions.getByType<org.gradle.accessors.dm.LibrariesForLibs>()

            dependencies {
                add("implementation", libs.kotlin.stdlib)
                add("implementation", libs.androidx.core.ktx)
                add("implementation", libs.androidx.appcompat)
                add("implementation", libs.androidx.lifecycle.extensions) // Consider removing/updating
                add("implementation", libs.androidx.lifecycle.reactivestreams) // Consider removing/updating
                add("implementation", libs.androidx.room.ktx)
                add("implementation", libs.androidx.room.runtime)

                add("implementation", libs.dagger)
                // For Dagger KSP:
                // add("ksp", libs.dagger.compiler) // Assuming you have libs.dagger.compiler

                add("implementation", libs.gson)

                add("implementation", platform(libs.firebase.bom))
                add("implementation", libs.firebase.firestore)
                add("implementation", libs.firebase.auth)
                add("implementation", libs.firebase.database)
                add("implementation", libs.firebase.storage)
                add("implementation", libs.firebase.appcheck.playintegrity)
                add("implementation", libs.firebase.functions)


                add("implementation", libs.androidx.work.runtime.ktx)
                add("implementation", libs.play.services.base)
                add("implementation", libs.play.services.basement)
                add("implementation", libs.squareup.okhttp)
                add("implementation", libs.jackson.module.kotlin)

                add("testImplementation", libs.junit)
                add("testImplementation", libs.kotlinx.coroutines.test)
                add("testImplementation", libs.mockito.core)
                add("testImplementation", libs.mockito.kotlin)

                // Example for KSP if Dagger compiler is added
                // add("ksp", libs.google.dagger.compiler)
            }
        }
    }
}

// Helper for kotlinOptions within AppExtension
fun AppExtension.kotlinOptions(block: org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions.() -> Unit) {
    (this as org.gradle.api.plugins.ExtensionAware).extensions.configure("kotlinOptions", block)
}
