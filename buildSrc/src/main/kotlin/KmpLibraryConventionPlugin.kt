import com.android.build.gradle.LibraryExtension
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("io.gitlab.arturbosch.detekt")

            extensions.configure<KotlinMultiplatformExtension> {
                androidTarget()
                jvm()
                jvmToolchain(17)

                sourceSets.apply {
                    getByName("commonTest") {
                        dependencies {
                            implementation(kotlin("test"))
                        }
                    }
                }
            }

            extensions.configure<LibraryExtension> {
                compileSdk = 34

                defaultConfig {
                    minSdk = 26
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
            }

            extensions.configure<DetektExtension> {
                buildUponDefaultConfig = true
                allRules = false
                config.setFrom(rootProject.files("config/detekt/detekt.yml"))
            }
        }
    }
}
