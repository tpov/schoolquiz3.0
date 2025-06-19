import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByName

class AndroidLibraryPublishConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // Apply the base Android library convention
            pluginManager.apply("android-library-convention")
            pluginManager.apply("maven-publish")

            extensions.configure<LibraryExtension> {
                // Any specific configurations for publishable libraries can go here
                // For example, ensuring sources or javadoc are generated if desired
            }

            // Configure publishing
            extensions.configure<PublishingExtension> {
                publications {
                    create<MavenPublication>("release") {
                        // groupId, artifactId, version will be set in the module's build.gradle.kts
                        // Example:
                        // groupId = "com.yourcompany"
                        // artifactId = project.name
                        // version = "1.0.0"

                        afterEvaluate { // Ensure components are ready
                            from(components.getByName("release")) // 'release' is the default component for Android libraries
                        }

                        // Optional: Add sources and javadoc artifacts
                        // artifact(tasks.findByName("androidSourcesJar") ?: tasks.register("androidSourcesJar", Jar::class.java) {
                        //     archiveClassifier.set("sources")
                        //     from(extensions.getByType(LibraryExtension::class.java).sourceSets.getByName("main").java.srcDirs)
                        // })
                        //
                        // artifact(tasks.findByName("androidJavadocsJar") ?: tasks.register("androidJavadocsJar", Jar::class.java) {
                        //     archiveClassifier.set("javadoc")
                        //     // Configure javadoc task if needed
                        // })
                    }
                }
                repositories {
                    mavenLocal() // Default to mavenLocal, can be configured in the root project
                    // Example for remote repo (taken from old publish.gradle.kts)
                    // maven {
                    //     name = "MyRepo" // Or from properties
                    //     url = uri("https://your.repo.url") // Or from properties
                    //     credentials {
                    //         username = findProperty("repoUser") as String? ?: System.getenv("REPO_USER")
                    //         password = findProperty("repoPassword") as String? ?: System.getenv("REPO_PASSWORD")
                    //     }
                    // }
                }
            }

            // It's good practice to ensure that publishing configuration (like groupId, artifactId, version)
            // is provided by the consuming module.
            // You could add checks here or rely on module-specific configuration.
        }
    }
}
