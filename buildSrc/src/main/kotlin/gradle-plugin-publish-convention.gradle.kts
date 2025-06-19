import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension

class GradlePluginPublishConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // Apply base plugins for a Gradle plugin module
            pluginManager.apply("org.jetbrains.kotlin.jvm") // Assuming it's a Kotlin plugin
            pluginManager.apply("java-gradle-plugin")      // For Gradle plugin development capabilities
            pluginManager.apply("maven-publish")           // For publishing to Maven repositories

            extensions.configure<GradlePluginDevelopmentExtension> {
                // Configure the plugin declaration if needed
                // plugins {
                //     create("myCustomPlugin") {
                //         id = "com.example.my-custom-plugin"
                //         implementationClass = "com.example.MyCustomPlugin"
                //     }
                // }
                // This part is usually configured in the plugin module's build.gradle.kts itself,
                // as plugin ID and implementation class are specific to the plugin.
            }

            // Configure publishing for the Gradle plugin
            // The `java-gradle-plugin` automatically creates a publication named 'pluginMaven'
            // and a component named 'java'.
            extensions.configure<PublishingExtension> {
                publications {
                    // The 'pluginMaven' publication is automatically configured by the 'java-gradle-plugin'.
                    // You might want to customize it, e.g., to add GString coordinates.
                    // getByName<MavenPublication>("pluginMaven") {
                        // groupId = "com.yourcompany.gradle" // Or from project properties
                        // artifactId = project.name // Or from project properties
                        // version = version // Or from project properties
                    // }

                    // If you need to publish other artifacts (e.g., sources, javadoc) along with the plugin
                    // you can configure the 'pluginMaven' publication or create a new one.
                }
                repositories {
                    mavenLocal() // Default to mavenLocal
                    // Configure other repositories as needed
                }
            }

            // It's crucial that groupId, artifactId, and version are defined in the
            // logger-gradle-plugin/build.gradle.kts file for publishing to work correctly.
            // Example:
            // group = "com.tpov.logger"
            // version = "1.0.1" // Update as needed

            // Dependencies for a Gradle plugin (e.g., Gradle API)
            // are usually added in the plugin module's build.gradle.kts.
            // val libs = extensions.getByType<org.gradle.accessors.dm.LibrariesForLibs>()
            // dependencies {
            //     compileOnly(gradleApi())
            //     implementation(libs.kotlin.stdlib)
            //     // ... other dependencies
            // }
        }
    }
}
