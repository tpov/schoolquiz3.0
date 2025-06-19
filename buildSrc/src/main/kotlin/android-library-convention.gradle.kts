import com.android.build.gradle.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("org.jetbrains.kotlin.android")
            pluginManager.apply("com.google.devtools.ksp") // Apply KSP by default for libraries too

            extensions.configure<LibraryExtension> {
                compileSdkVersion(34) // Consider moving to a version catalog or ext property

                defaultConfig {
                    minSdk = 26
                    // targetSdk = 34 // targetSdk is not typically set in libraries, but compileSdk is used
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    consumerProguardFiles("consumer-rules.pro")
                    multiDexEnabled = true // Though less common for libraries, good to have from root
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
                     viewBinding = true // Enable if libraries also use view binding
                    // buildConfig = true // Enable if you need BuildConfig fields in libraries
                }

                buildTypes {
                    getByName("release") {
                        isMinifyEnabled = false // Libraries are usually not minified themselves
                        // proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") // Proguard rules for libraries are usually consumer rules
                    }
                }
            }

            val libs = extensions.getByType<org.gradle.accessors.dm.LibrariesForLibs>()

            dependencies {
                add("implementation", libs.kotlin.stdlib)
                add("implementation", libs.androidx.core.ktx)
                // Libraries might not always need appcompat, depends on what they do.
                // add("implementation", libs.androidx.appcompat)

                // Common Android dependencies - adjust as needed for your libraries
                // add("implementation", libs.androidx.lifecycle.extensions)
                // add("implementation", libs.androidx.lifecycle.reactivestreams)
                // add("implementation", libs.androidx.room.ktx)
                // add("implementation", libs.androidx.room.runtime)

                // add("implementation", libs.dagger) // If libraries use Dagger
                // add("ksp", libs.dagger.compiler) // If libraries use Dagger

                // add("implementation", libs.gson) // If libraries use Gson

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

// Helper for kotlinOptions within LibraryExtension
fun LibraryExtension.kotlinOptions(block: org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions.() -> Unit) {
    (this as org.gradle.api.plugins.ExtensionAware).extensions.configure("kotlinOptions", block)
}
