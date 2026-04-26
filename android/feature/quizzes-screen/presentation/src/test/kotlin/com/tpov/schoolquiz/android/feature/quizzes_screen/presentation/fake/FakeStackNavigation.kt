package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake

import com.arkivanov.decompose.Cancellation
import com.arkivanov.decompose.router.stack.StackNavigation
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig

/**
 * Test-only StackNavigation that records navigation intents.
 * Delegates to an in-memory list to keep state consistent for assertions.
 *
 * Reserved for Phase-04 DefaultQuestListComponentTest (QL-U-05..07, SL-U-04, TH-U-04, LL-U-04)
 * where child components receive StackNavigation<QuizzesConfig> and pushNew calls are verified.
 */
class FakeStackNavigation : StackNavigation<QuizzesConfig> {

    private val stack = mutableListOf<QuizzesConfig>(QuizzesConfig.Idle)
    val pushedConfigs = mutableListOf<QuizzesConfig>()
    val poppedToIndices = mutableListOf<Int>()
    private var _popCalls = 0
    val popCalls: Int get() = _popCalls

    override fun navigate(
        transformer: (List<QuizzesConfig>) -> List<QuizzesConfig>,
        onComplete: (newStack: List<QuizzesConfig>, oldStack: List<QuizzesConfig>) -> Unit,
    ) {
        val old = stack.toList()
        val new = transformer(old)
        // Record any configs pushed to the end of the stack (pushNew pattern: new = old + [config])
        if (new.size > old.size) {
            pushedConfigs.addAll(new.drop(old.size))
        }
        stack.clear()
        stack.addAll(new)
        onComplete(new, old)
    }

    override fun subscribe(observer: (StackNavigation.Event<QuizzesConfig>) -> Unit): Cancellation =
        Cancellation { }
}
