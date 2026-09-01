package com.tpov.schoolquiz.shared.feature.economy.domain.model

/**
 * Настройки экономики зарядов — таблица, которой владеет сервер.
 *
 * Смысл всей затеи в том, что ни одно число отсюда не требует релиза: подкрутить цену турнира или
 * потолок зарядов должно быть решением на сервере, а не сборкой в магазине. Поэтому таблица живёт
 * одним документом в `configs/`, закрытом для клиентов правилами, и приезжает на устройство
 * вызовом, свёрнутым в обычную синхронизацию.
 *
 * Значения ниже — **загрузочная копия**. Она нужна ровно затем, чтобы устройство, которое ещё ни
 * разу не синхронизировалось, вообще запустилось, и не является источником истины ни для чего, что
 * решает сервер. Как только приехала настоящая таблица, копия больше не читается.
 *
 * @property version монотонная версия таблицы. По ней синхронизация пропускает неизменившуюся
 *   таблицу, а запись аудита называет, по какой таблице принято решение.
 */
data class EconomyConstants(
    val version: Long = BOOTSTRAP_VERSION,
    val standard: ChargeRules = ChargeRules.STANDARD_BOOTSTRAP,
    val plasma: ChargeRules = ChargeRules.PLASMA_BOOTSTRAP,
    /** Прейскурант: сколько очков стоит попытка каждого вида. */
    val activityPrices: Map<ActivityKind, Int> = BOOTSTRAP_PRICES,
    /**
     * Запас на расхождение часов, прежде чем заявка считается перерасходом.
     *
     * Ноль: обвинять игрока в подделке из-за криво идущих часов нельзя, но и щедрость здесь
     * назначается осознанно, а не по умолчанию.
     */
    val clockSkewToleranceMs: Long = 0L,
    /** Пишутся ли записи аудита о перерасходе. */
    val auditEnabled: Boolean = true,
) {
    init {
        require(version >= 0L) { "EconomyConstants.version must be non-negative, got $version" }
        require(clockSkewToleranceMs >= 0L) { "clockSkewToleranceMs must be non-negative" }
        require(activityPrices.keys.containsAll(ActivityKind.entries.toSet())) {
            "прейскурант обязан назвать цену каждому виду; нет цены у " +
                (ActivityKind.entries.toSet() - activityPrices.keys)
        }
        require(activityPrices.values.all { it >= 0 }) { "цена не может быть отрицательной" }
    }

    fun rulesFor(kind: ChargeKind): ChargeRules =
        when (kind) {
            ChargeKind.STANDARD -> standard
            ChargeKind.PLASMA -> plasma
        }

    /**
     * Сколько очков стоит попытка.
     *
     * Платят только обычные заряды: плазменный за попытку не берётся вовсе — он покупает пропуск
     * сложного вопроса и ничего больше.
     */
    fun priceOf(activity: ActivityKind): Int = activityPrices.getValue(activity)

    companion object {
        /**
         * Очков в одном заряде.
         *
         * Не настройка: прейскурант выражен в очках, и менять их цену значит менять смысл всех
         * чисел разом. Досталось от `LIFE_POINTS_PER_HEART` и намеренно не изменилось.
         */
        const val POINTS_PER_CHARGE: Int = 100

        /** Версия загрузочной копии. Настоящая таблица начинается с единицы. */
        const val BOOTSTRAP_VERSION: Long = 0L

        /**
         * Начальный прейскурант.
         *
         * Полный бак — десять зарядов, тысяча очков: два турнира, три экзамена или тридцать
         * обычных уроков. Обычный заряд восстанавливается за час, то есть очко за 36 секунд, и
         * турнир стоит пяти часов восстановления.
         */
        val BOOTSTRAP_PRICES: Map<ActivityKind, Int> =
            mapOf(
                // Ровно столько стоил урок и в старом приложении — `COAST_LIFE_HOME_QUIZ`.
                ActivityKind.ORDINARY_LESSON to 33,
                // Было 33, поднято до половины заряда.
                ActivityKind.ARENA to 50,
                ActivityKind.THEME_TEST to POINTS_PER_CHARGE,
                ActivityKind.FINAL_EXAM to 3 * POINTS_PER_CHARGE,
                ActivityKind.TOURNAMENT to 5 * POINTS_PER_CHARGE,
            )

        /** Загрузочная копия целиком. */
        val BOOTSTRAP: EconomyConstants = EconomyConstants()
    }
}

/**
 * Правила одного вида заряда.
 *
 * @property maxOwned потолок. Понизить его можно, и это никого не обкрадывает — см.
 *   `ChargeRegeneration`.
 * @property regenMs за сколько восстанавливается один целый заряд. Ноль — не восстанавливается.
 * @property priceLadder цена каждого следующего слота. Индекс — сколько зарядов уже есть.
 * @property requiresSettledAccount нельзя тратить, пока есть неучтённые заявки. Так плазма и
 *   становится онлайновой: монетарный ресурс не расходуется вслепую.
 */
data class ChargeRules(
    val maxOwned: Int,
    val regenMs: Long,
    val priceLadder: List<Long>,
    val currency: ShopCurrency,
    val requiresSettledAccount: Boolean = false,
) {
    init {
        require(maxOwned >= 0) { "ChargeRules.maxOwned must be non-negative, got $maxOwned" }
        require(regenMs >= 0L) { "ChargeRules.regenMs must be non-negative, got $regenMs" }
        require(priceLadder.isNotEmpty()) { "лестница цен не может быть пустой: слот нельзя купить даром" }
        require(priceLadder.all { it >= 0L }) { "цена слота не может быть отрицательной" }
    }

    /**
     * Цена слота, когда на руках уже [owned] зарядов.
     *
     * Лестница короче потолка — не ошибка: последняя ступень повторяется. Это осознанно, потому что
     * поднять потолок на сервере проще, чем помнить, что вместе с ним надо удлинить и лестницу, а
     * молчаливый ноль за слот сверх лестницы был бы бесплатным зарядом.
     */
    fun slotPrice(owned: Int): Long = priceLadder[owned.coerceIn(0, priceLadder.lastIndex)]

    /** Восстанавливается ли этот вид сам. */
    val regenerates: Boolean get() = regenMs > 0L

    companion object {
        /**
         * Обычный заряд: десять на руках, час на восстановление одного.
         *
         * Потолок был пять, стал десять; восстановление не изменилось.
         */
        val STANDARD_BOOTSTRAP: ChargeRules =
            ChargeRules(
                maxOwned = 10,
                regenMs = 60L * 60 * 1000,
                priceLadder = listOf(1_000L, 2_000L, 5_000L, 10_000L, 20_000L),
                currency = ShopCurrency.NOLICS,
            )

        /**
         * Плазменный: три на руках, сутки на восстановление одного.
         *
         * Сутки, а не восемь часов, — это прямой ответ на опасение, что три пропуска сложных
         * вопросов в день слишком жирно. Лестница `1, 2, 3` золотом: все три стоят шесть, один раз.
         * Прежняя цена была десять золотом за единственный, плоско.
         */
        val PLASMA_BOOTSTRAP: ChargeRules =
            ChargeRules(
                maxOwned = 3,
                regenMs = 24L * 60 * 60 * 1000,
                priceLadder = listOf(1L, 2L, 3L),
                currency = ShopCurrency.GOLD,
                requiresSettledAccount = true,
            )
    }
}
