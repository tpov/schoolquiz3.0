package com.tpov.schoolquiz.shared.feature.ads.domain

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Инварианты дома рекламы — исходник-сканы, не обещания (история 3.4).
 *
 * Урок закрыт для рекламы не комментарием, а отсутствием упоминаний в его исходниках; SDK AdMob
 * в дереве не появляется — его имя живёт в одной константе, которой выключатель читает манифест.
 * Скан хрупок к переезду каталогов нарочно: переезд сломает тест, и кто переезжает — тот и
 * поправит пути, зная, что проверка осталась.
 */
class AdsHomePinsTest {

    /** Корень репозитория: build/…/classes → shared/feature/ads/domain → shared/feature/ads → shared → корень. */
    private val repoRoot: File = File(System.getProperty("user.dir"))
        .let { start -> generateSequence(start) { it.parentFile }.firstOrNull { File(it, "settings.gradle.kts").exists() } ?: start }

    private fun sources(root: File): Sequence<File> =
        root.walkTopDown()
            .filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".kts")) && "/build/" !in it.path.replace('\\', '/') }

    private fun lessonRunnerSources(): Sequence<File> =
        sequenceOf("shared/feature/lesson-runner", "android/feature/lesson-runner")
            .flatMap { relative -> sources(File(repoRoot, relative)) }

    @Test
    fun `the lesson never mentions an ad surface`() {
        // «feature.ads» ловит импорты, «feature:ads» — gradle-зависимость, добавленную без типа.
        val forbidden = listOf("feature.ads", "feature:ads", "AdPlacement", "AdsGate", "AdsAvailability", "gms.ads")
        val offenders = lessonRunnerSources()
            .flatMap { file -> forbidden.filter { token -> token in file.readText() }.map { token -> file.name to token } }
            .toList()
        assertTrue(offenders.isEmpty(), "урок упомянул рекламу: $offenders")
    }

    @Test
    fun `the AdMob SDK exists in the tree only as the metadata key constant`() {
        // Событие аналитики RewardedAdCompleted — своё, не SDK; пины ищут классы и пакеты SDK.
        val sources = sequenceOf("shared", "android", "apps")
            .flatMap { relative -> sources(File(repoRoot, relative)) }
            .filter { "/src/test/" !in it.path.replace('\\', '/') && "/src/jvmTest/" !in it.path.replace('\\', '/') }
            .toList()
        val offenders = sources
            .filter { !it.path.contains("feature${File.separatorChar}ads") }
            .flatMap { file ->
                listOf("gms.ads", "play-services-ads", "AdView", "com.google.android.ads")
                    .filter { token -> token in file.readText() }
                    .map { token -> file.name to token }
            }
            .toList()
        assertTrue(offenders.isEmpty(), "рекламный SDK мелькнул вне дома рекламы: $offenders")
    }
}
