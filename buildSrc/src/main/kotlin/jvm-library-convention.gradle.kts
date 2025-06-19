import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions

class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("java-library") // Apply java-library for api/implementation configurations

            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }

            extensions.configure<KotlinJvmOptions> {
                jvmTarget = "17"
            }

            // Accessing libs from version catalog
            val libs = extensions.getByType<org.gradle.accessors.dm.LibrariesForLibs>()

            dependencies {
                // Common dependencies for JVM libraries
                add("implementation", libs.kotlin.stdlib)
                // Add other common JVM dependencies if any, e.g., kotlinx.coroutines.core
                // add("api", libs.kotlinx.coroutines.core) // Example if it's an API dependency

                add("testImplementation", libs.junit)
                add("testImplementation", libs.kotlinx.coroutines.test)
                add("testImplementation", libs.mockito.core)
                add("testImplementation", libs.mockito.kotlin)
            }
        }
    }
}
