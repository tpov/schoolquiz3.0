import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidComposeLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("schoolquiz.android.library")

            extensions.configure<LibraryExtension> {
                buildFeatures {
                    compose = true
                }
                composeOptions {
                    // Kotlin 1.9.22 → Compose compiler 1.5.10 (fixed pair per compatibility table)
                    kotlinCompilerExtensionVersion = "1.5.10"
                }
            }
        }
    }
}
