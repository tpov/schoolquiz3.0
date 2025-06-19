import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByName

class JvmLibraryPublishConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // Apply the base JVM library convention
            pluginManager.apply("jvm-library-convention")
            pluginManager.apply("maven-publish")

            extensions.configure<PublishingExtension> {
                publications {
                    create<MavenPublication>("release") { // Changed from "maven" to "release" for consistency
                        // groupId, artifactId, version will be set in the module's build.gradle.kts
                        // artifactId defaults to project.name if not set in publication block
                        afterEvaluate { // Ensure components are ready
                           from(components.getByName("java"))
                        }
                    }
                }
                repositories {
                    mavenLocal() // Default to mavenLocal
                    // Configure other repositories as needed in root or consuming project
                }
            }
        }
    }
}
