package com.tpov.schoolquiz.shared.core.outbox

/**
 * Что делать с очередью перед сменой аккаунта и после неё (AD-8).
 *
 * Сегодня этот путь реален и ничем не обслужен: вход через Google при коллизии делает
 * `signInWithCredential`, а не привязку, и записи очереди с прежним `uid` после переключения будут
 * получать отказ сервера вечно — их владельца больше нет.
 *
 * Две стороны одного правила. **До** переключения — слить: после него прежнего `uid` уже не узнать,
 * а запись принадлежит тому, кто её создал, и под другим аккаунтом не отправляется. **После** — снять
 * следы прежнего владельца, и ровно две операции, каждая с закрытым перечнем: записи очереди
 * ([OutboxStore.forget]) и личные следы вне очереди ([OwnerTraces]). Между ними — согласие игрока,
 * которое здесь не спрашивается: предупреждение и есть согласие, и его показывает экран.
 */
class AccountSwitchGuard(
    private val engine: OutboxEngine,
    private val store: OutboxStore,
    private val remainder: OutboxRemainderSource,
    private val traces: OwnerTraces,
) {
    /**
     * Пытается отправить всё, что ждёт у [uid], и говорит, что осталось и у кого.
     *
     * Остаток считается по всем пяти состояниям, а не по выборке «что поедет»: удаляются потом все
     * пять, и разойтись этим двум числам нельзя — иначе игрок с одной конфликтной записью
     * переключается без вопроса, а запись, ждавшая его же решения (AD-24), исчезает молча.
     *
     * Пустой [uid] — не «терять нечего». Действующей сессии может не быть (аккаунт удалён на
     * сервере, токен отозван), а строки прежнего владельца при этом лежат в таблице; кто он, знает
     * очередь, и спрашивается это у неё. Сливать в этом случае нечем — отправлять под несуществующей
     * сессией некуда, — но предупредить и снять следы обязательно.
     *
     * Не бросает: неудача слива — это не повод не дать сменить аккаунт, это повод предупредить.
     * Решение принимает игрок, а не очередь.
     */
    suspend fun flushBefore(uid: String): SwitchReadiness {
        val owners =
            runCatching { resolveOwners(uid) }
                // База не ответила — но кто уходит, известно точно: он пришёл параметром. Пустой
                // набор здесь означал бы «забывать некого», и следы действующего владельца
                // остались бы на устройстве навсегда: после входа его `uid` уже не узнать.
                .getOrElse { return unknown(owners = listOfNotNull(uid.takeIf { it.isNotBlank() })) }
        if (owners.isEmpty()) return SwitchReadiness.clean()
        // Слив идёт только под действующей сессией: без неё транспорту некуда идти, а записи
        // прежнего владельца под чужим `uid` не отправляются по правилу истории 6.1.
        if (uid.isNotBlank()) runCatching { engine.drain(uid) }
        return runCatching {
            SwitchReadiness(
                remainder = owners.fold(OutboxRemainder.NONE) { sum, owner -> sum + remainder.remainderOf(owner) },
                isKnown = true,
                namedLosses = lossesOf(owners),
                owners = owners,
            )
        }.getOrElse { unknown(owners) }
    }

    /**
     * Снимает следы прежних владельцев — уже после того, как вход под новым аккаунтом состоялся.
     *
     * Порядок закреплён историей 6.4: сначала записи очереди, потом личные следы. Всё это идёт под
     * замком движка — плановый проход, совпавший по времени, не успеет отправить запись, которая
     * вот-вот будет вырезана, а следующий за ним пойдёт уже под новым `uid`.
     *
     * Бросает, если база не ответила: молча оставить чужие строки — то самое расхождение, которое
     * AD-8 запрещает. Вызывающий сообщает об этом словами, аккаунт при этом уже сменён.
     */
    suspend fun forgetAfterSwitch(previousUids: List<String>) {
        val owners = previousUids.filter { it.isNotBlank() }.distinct()
        if (owners.isEmpty()) return
        engine.withoutPasses {
            owners.forEach { owner ->
                store.forget(owner)
                traces.forget(owner, OwnerForgetReason.SWITCH)
            }
        }
    }

    /** Sign-in and cleanup use the same pass mutex: no acknowledged old-owner write can race it. */
    suspend fun switchAndForget(
        previousUids: List<String>,
        signIn: suspend () -> String,
    ): String =
        engine.withoutPasses {
            val newUid = signIn()
            previousUids.filter { it.isNotBlank() && it != newUid }.distinct().forEach { owner ->
                store.forget(owner)
                traces.forget(owner, OwnerForgetReason.SWITCH)
            }
            newUid
        }

    /**
     * Кого предстоит забыть — по следам, а не по одной очереди.
     *
     * Пустая очередь — обычное стационарное состояние, а не «терять нечего»: запись уехала и
     * исчезла, а черновик остался лежать в `REVIEW_SENT`. Пока набор владельцев спрашивался у
     * одной очереди, игрок с локальными черновиками и пустой очередью менял аккаунт без вопроса и
     * без уборки: чужие строки оставались на устройстве, читающий проход просил у сервера чужие
     * документы и получал отказ правила на каждом прогоне.
     *
     * Действующая сессия в набор входит наравне с остальными, а не вместо них: неудавшееся снятие
     * следов прошлого переключения оставляет на устройстве третьего владельца, и другого случая
     * его забыть уже не будет.
     */
    private suspend fun resolveOwners(uid: String): List<String> =
        (listOf(uid) + remainder.ownersWithRecords() + traces.ownersWithTraces())
            .filter { it.isNotBlank() }
            .distinct()

    /**
     * Что из названного игроку у этих владельцев действительно лежит на устройстве.
     *
     * Перечень видов закрыт, но называть все три подряд — значит просить согласие на потерю того,
     * чего нет. Порядок — перечисления вида, чтобы вопрос не менял вид от порядка владельцев.
     */
    private suspend fun lossesOf(owners: List<String>): List<OwnerDataKind> {
        val found = owners.flatMapTo(mutableSetOf()) { traces.lossesOf(it) }
        return OwnerDataKind.entries.filter { it in found }
    }

    /** Даже сосчитать не удалось — база не ответила. Незнание тоже повод спросить, а не молчать. */
    private fun unknown(owners: List<String>) =
        SwitchReadiness(
            remainder = OutboxRemainder.NONE,
            isKnown = false,
            namedLosses = traces.namedLosses,
            owners = owners,
        )
}

/**
 * Вторая операция AD-8: личные следы прежнего владельца, лежащие вне очереди.
 *
 * Перечень закрыт и живёт у реализации — курсор `private_quests:{uid}`
 * и локальные строки владельца, названные историей 6.2. Общий контент и его курсоры (`catalogs`,
 * `catalog_sync:{catalogId}`, `lesson_content:{lessonId}`) от аккаунта не зависят и остаются.
 * Очередь эта операция не трогает: её судьба решается [OutboxStore.forget], а не этим вызовом.
 */
interface OwnerTraces {
    suspend fun forget(uid: String)

    /** Legacy implementations keep their erase behavior. Only the documented Arena journal opts out. */
    suspend fun forget(
        uid: String,
        reason: OwnerForgetReason,
    ) = forget(uid)

    /**
     * Все, кто оставил на устройстве личные строки.
     *
     * Симметрично [OutboxRemainderSource.ownersWithRecords] и по тому же поводу: набор владельцев
     * нельзя выводить из одной очереди. Очередь пустеет сама — запись уехала и исчезла, — а
     * черновик, статистика и история ответов лежат дальше. Спрашивается это у владельца второй
     * операции, потому что перечень таблиц знает он один.
     */
    suspend fun ownersWithTraces(): List<String>

    /**
     * Что из [namedLosses] у этого владельца действительно есть.
     *
     * Отдельный вопрос, а не постоянный список: предупреждение — это согласие, и просить его на
     * потерю несуществующей истории ответов значит спрашивать не о том, что удаляется.
     */
    suspend fun lossesOf(uid: String): List<OwnerDataKind>

    /**
     * Виды данных, у которых нет серверного двойника и потерю которых игрок заметит.
     *
     * Единственный источник того, что называет предупреждение (история 6.2): имена таблиц и SQL
     * наружу не выходят (NFR11), а число «шесть действий» ничего не говорит про историю ответов и
     * неопубликованные черновики. Это перечень «что вообще бывает»; чего лишится конкретный
     * владелец, отвечает [lossesOf]. Спрашивается тогда, когда база не ответила и узнать точнее
     * нечем.
     */
    val namedLosses: List<OwnerDataKind>
}

/** No-reason erase APIs still erase. An ordinary sign-out/switch is the narrow retention exception. */
enum class OwnerForgetReason { SWITCH, SIGN_OUT, ERASE_LOCAL_DATA, DELETE_ACCOUNT }

/**
 * Вид данных, называемый игроку перед сменой аккаунта.
 *
 * Закрытый набор: имя, а не таблица. Слова подбирает экран, перечень — владелец второй операции.
 */
enum class OwnerDataKind {
    /** История ответов на вопросы. */
    ANSWER_HISTORY,

    /** Расписание повторов. */
    REPETITION_SCHEDULE,

    /** Неопубликованные черновики квестов. */
    UNPUBLISHED_QUEST_DRAFTS,
}

/**
 * Насколько безопасно менять аккаунт прямо сейчас.
 *
 * @property remainder сколько строк и в каком состоянии останется неотправленными.
 * @property isKnown удалось ли вообще узнать числа. Незнание — тоже повод предупредить, и оно не
 *   то же самое, что «ничего не осталось».
 * @property namedLosses виды данных без серверного двойника, которые у названных владельцев
 *   действительно лежат на устройстве и исчезнут вместе с аккаунтом. Пусто — терять нечего; при
 *   `isKnown = false` это перечень «что вообще бывает», потому что точнее узнать нечем.
 * @property owners чьи строки предстоит снять после входа. Обычно один прежний `uid`; плюс все,
 *   у кого остались записи очереди или личные строки, — их называет не аутентификация, а база.
 */
data class SwitchReadiness(
    val remainder: OutboxRemainder,
    val isKnown: Boolean,
    val namedLosses: List<OwnerDataKind>,
    val owners: List<String>,
) {
    /**
     * Можно менять молча: всё уехало и терять нечего.
     *
     * Пустой очереди мало. Черновик, история ответов и расписание повторов сервером не хранятся, и
     * молчаливое переключение стирало их без единого вопроса — согласие на это никто не давал
     * (история 6.4).
     */
    val isClean: Boolean get() = isKnown && remainder.isEmpty && namedLosses.isEmpty()

    /** Игрока надо предупредить словами, что последние действия могут не сохраниться. */
    val needsWarning: Boolean get() = !isClean

    companion object {
        fun clean(namedLosses: List<OwnerDataKind> = emptyList()) =
            SwitchReadiness(
                remainder = OutboxRemainder.NONE,
                isKnown = true,
                namedLosses = namedLosses,
                owners = emptyList(),
            )
    }
}
