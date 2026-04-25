import com.android.build.gradle.LibraryExtension
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("org.jetbrains.kotlin.android")
            pluginManager.apply("io.gitlab.arturbosch.detekt")
            pluginManager.apply("org.jlleitschuh.gradle.ktlint")

            extensions.configure<LibraryExtension> {
                compileSdk = 35

                defaultConfig {
                    minSdk = 26
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    consumerProguardFiles("consumer-rules.pro")
                }

                buildTypes {
                    named("release") {
                        isMinifyEnabled = false
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro",
                        )
                    }
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }

                buildFeatures {
                    viewBinding = true
                }
            }

            tasks.withType(KotlinCompile::class.java).configureEach {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }

            extensions.configure<DetektExtension> {
                buildUponDefaultConfig = true
                allRules = false
                config.setFrom(rootProject.files("config/detekt/detekt.yml"))
            }

            extensions.configure<KtlintExtension> {
                version.set("1.1.1")
                android.set(true)
                ignoreFailures.set(false)
                filter {
                    exclude { it.file.path.contains("/build/") }
                    exclude { it.file.path.contains("/generated/") }
                    exclude { it.file.path.contains("/test/") }
                    exclude { it.file.path.contains("/androidTest/") }
                }
            }
        }
    }
}
