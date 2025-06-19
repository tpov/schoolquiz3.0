import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions

class JvmApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("application") // Standard Gradle application plugin

            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }

            extensions.configure<JavaApplication> {
                // mainClass is usually set in the consuming project's build file
                // applicationDefaultJvmArgs = listOf("-Xms512m", "-Xmx1g") // Example
            }

            extensions.configure<KotlinJvmOptions> {
                jvmTarget = "17"
            }

            val libs = extensions.getByType<org.gradle.accessors.dm.LibrariesForLibs>()

            dependencies {
                add("implementation", libs.kotlin.stdlib)
                // Add other common JVM application dependencies if any
                // e.g. test dependencies like junit, mockito could be added here too
                add("testImplementation", libs.junit)
                add("testImplementation", libs.kotlinx.coroutines.test) // If using coroutines
                add("testImplementation", libs.mockito.core)
            }
        }
    }
}
