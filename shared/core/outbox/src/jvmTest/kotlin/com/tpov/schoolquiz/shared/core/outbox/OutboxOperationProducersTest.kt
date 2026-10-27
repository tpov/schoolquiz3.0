package com.tpov.schoolquiz.shared.core.outbox

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * У каждой объявленной операции есть тот, кто её создаёт.
 *
 * `OutboxOperationsRegistryTest` сверяет имена клиента и приёмника, и обе стороны у него зелёные
 * даже тогда, когда записи с этим именем не создаёт никто: имя объявлено, обработчик на сервере
 * зарегистрирован, привязка карантина в composition root стоит — а производителя нет. Ровно так
 * и жила разблокировка урока: она уходила прямым callable и без сети отвечала отказом вместо
 * отложенного действия, а реестр, маршрутизатор карантина и контракт согласованно утверждали,
 * что путь есть.
 *
 * Производителем считается production-файл владеющей фичи, который и называет операцию, и строит
 * строку очереди. Ни composition root, ни само ядро производителями не считаются: привязка и
 * объявление — это не запись в таблице.
 */
class OutboxOperationProducersTest {
    private val repoRoot = File("../../../")

    /** Имя константы и её значение: имя нужно, чтобы найти обращение через `OutboxOperations.`. */
    private val declared =
        listOf(
            "UNLOCK_LESSON" to OutboxOperations.UNLOCK_LESSON,
            "SUBMIT_ATTEMPT" to OutboxOperations.SUBMIT_ATTEMPT,
            "OPEN_GIFT_BOX" to OutboxOperations.OPEN_GIFT_BOX,
            "READY_ACK" to OutboxOperations.READY_ACK,
            "SUBMIT_RATING" to OutboxOperations.SUBMIT_RATING,
            "SUBMIT_ARENA" to OutboxOperations.SUBMIT_ARENA,
            "SET_SHELF" to OutboxOperations.SET_SHELF,
            "CANCEL_NICKNAME_LISTING" to OutboxOperations.CANCEL_NICKNAME_LISTING,
            "CANCEL_LOGO_LISTING" to OutboxOperations.CANCEL_LOGO_LISTING,
        )

    /** Production-исходники фич и платформы: без `build/`, без тестовых наборов, без ядра и app. */
    private val productionSources: List<File> by lazy {
        listOf("shared", "platform", "android")
            .map { File(repoRoot, it) }
            .filter { it.isDirectory }
            .flatMap { root ->
                root.walkTopDown()
                    .onEnter { it.name != "build" }
                    .filter { it.isFile && it.extension == "kt" }
                    .toList()
            }
            .filterNot { file ->
                val path = file.path.replace(File.separatorChar, '/')
                "/src/commonTest/" in path ||
                    "/src/test/" in path ||
                    "/src/jvmTest/" in path ||
                    "/src/androidTest/" in path ||
                    "/src/androidInstrumentedTest/" in path ||
                    "/shared/core/outbox/" in path
            }
    }

    @Test
    fun `given every declared operation then some feature actually writes a row for it`() {
        assertTrue(productionSources.isNotEmpty(), "не нашёл ни одного исходника от ${repoRoot.absolutePath}")

        val orphans =
            declared.filter { (constant, value) ->
                productionSources.none { file ->
                    val text = file.readText()
                    val builds = "OutboxRecord(" in text || "OutboxEntity(" in text
                    val names = "OutboxOperations.$constant" in text || "\"$value\"" in text
                    builds && names
                }
            }.map { it.second }

        assertEquals(
            emptyList(),
            orphans,
            "эти операции объявлены и зарегистрированы на сервере, но строку очереди для них не " +
                "создаёт никто — путь существует только на бумаге: $orphans",
        )
    }
}
