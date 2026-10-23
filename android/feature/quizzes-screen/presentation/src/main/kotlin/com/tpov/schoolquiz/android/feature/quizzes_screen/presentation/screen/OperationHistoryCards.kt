package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.components.BrandCard
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGold
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT2
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.R
import com.tpov.schoolquiz.shared.feature.economy.domain.model.OperationHistoryRecord

/**
 * Карта записи истории операций (UX-20/AD-30, история 8.6) — общая для экрана-шторки
 * («История операций») и «Начислений» авторского урока.
 *
 * Свёрнута: дата не нужна (она в заголовке дня), видны повод-метка, подписанная сумма и цвет
 * ресурса. Раскрыта: полный разрешённый набор полей контракта — повод, ресурс, сумма, статус и
 * привязка к уроку, когда она есть. Разные ресурсы никогда не суммируются и рисуются разными
 * ролями цвета (UX-20/UX-4: нолики — серые, золото — золотое).
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
public fun HistoryRecordCard(record: OperationHistoryRecord) {
    var expanded by remember(record.id) { mutableStateOf(false) }
    BrandCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = reasonLabel(record.reason),
                    style = NoirType.rowSub,
                    color = NoirT1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = signedAmount(record),
                    style = NoirType.rowSub,
                    color = amountColor(record),
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ResourceDot(record.currency)
                    Text(
                        text = currencyLabel(record.currency),
                        style = NoirType.rowSub,
                        color = NoirT2,
                    )
                }
                Text(
                    text = statusLabel(record.status),
                    style = NoirType.rowSub,
                    color = NoirT2,
                )
            }
            if (expanded) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(color = NoirT2.copy(alpha = 0.08f), shape = RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    RecordLine(stringResource(R.string.history_label_reason), reasonLabel(record.reason))
                    RecordLine(stringResource(R.string.history_label_resource), currencyLabel(record.currency))
                    RecordLine(stringResource(R.string.history_label_amount), signedAmount(record))
                    RecordLine(stringResource(R.string.history_label_status), statusLabel(record.status))
                    record.lessonId?.let { lessonId ->
                        RecordLine(stringResource(R.string.history_label_lesson), lessonId)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordLine(
    label: String,
    value: String,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = NoirType.rowSub, color = NoirT2)
        Text(text = value, style = NoirType.rowSub, color = NoirT1)
    }
}

/** Роль цвета ресурса (UX-20/UX-4): нолики серые, золото золотое, остальное — роли акцентов. */
@Composable
private fun amountColor(record: OperationHistoryRecord): Color =
    when (record.currency) {
        "gold" -> NoirGold
        else ->
            if (record.isIncome) {
                LocalNoirAccent.current
            } else {
                NoirT1
            }
    }

@Composable
private fun ResourceDot(currency: String) {
    val color =
        when (currency) {
            "nolics" -> NoirT2
            "gold" -> NoirGold
            "skillPoints" -> LocalNoirAccent.current
            "premiumMs" -> LocalNoirAccent.current
            else -> NoirT2
        }
    androidx.compose.foundation.layout.Box(
        modifier =
            Modifier
                .padding(vertical = 2.dp)
                .background(color = color, shape = RoundedCornerShape(50)),
    ) {
        Text(text = " ", style = NoirType.rowSub)
    }
}

fun signedAmount(record: OperationHistoryRecord): String =
    if (record.isIncome) "+${record.delta}" else record.delta.toString()

/** Повод — закрытый список книги; знакомые поводы локализованы, незнакомый едет как есть. */
@Composable
fun reasonLabel(reason: String): String =
    when (reason) {
        "attempt_reward" -> stringResource(R.string.history_reason_attempt_reward)
        "attempt_toll" -> stringResource(R.string.history_reason_attempt_toll)
        "charge_claim" -> stringResource(R.string.history_reason_charge_claim)
        "slot_purchase" -> stringResource(R.string.history_reason_slot_purchase)
        "lesson_unlock" -> stringResource(R.string.history_reason_lesson_unlock)
        "gold_pack" -> stringResource(R.string.history_reason_gold_pack)
        "box_pack" -> stringResource(R.string.history_reason_box_pack)
        "purchase_refund" -> stringResource(R.string.history_reason_purchase_refund)
        "gift_box" -> stringResource(R.string.history_reason_gift_box)
        "market" -> stringResource(R.string.history_reason_market)
        "arena_milestone" -> stringResource(R.string.history_reason_arena_milestone)
        "arena_season" -> stringResource(R.string.history_reason_arena_season)
        "arena_ticket_reserve" -> stringResource(R.string.history_reason_arena_ticket_reserve)
        "arena_ticket_cancel" -> stringResource(R.string.history_reason_arena_ticket_cancel)
        "arena_author_royalty" -> stringResource(R.string.history_reason_arena_author_royalty)
        "review_fee" -> stringResource(R.string.history_reason_review_fee)
        "review_reward" -> stringResource(R.string.history_reason_review_reward)
        "premium_gold_purchase" -> stringResource(R.string.history_reason_premium_gold_purchase)
        "offer_bid" -> stringResource(R.string.history_reason_offer_bid)
        "offer_bid_refund" -> stringResource(R.string.history_reason_offer_bid_refund)
        "season_leaderboard" -> stringResource(R.string.history_reason_season_leaderboard)
        "season_referral" -> stringResource(R.string.history_reason_season_referral)
        "referral_bonus" -> stringResource(R.string.history_reason_referral_bonus)
        else -> reason
    }

@Composable
fun currencyLabel(currency: String): String =
    when (currency) {
        "nolics" -> stringResource(R.string.history_currency_nolics)
        "skillPoints" -> stringResource(R.string.history_currency_skill_points)
        "standardChargePoints" -> stringResource(R.string.history_currency_standard_charges)
        "plasmaChargePoints" -> stringResource(R.string.history_currency_plasma_charges)
        "gold" -> stringResource(R.string.history_currency_gold)
        "boxes" -> stringResource(R.string.history_currency_boxes)
        "premiumMs" -> stringResource(R.string.history_currency_premium)
        else -> currency
    }

@Composable
fun statusLabel(status: String): String =
    when (status) {
        "CONFIRMED" -> stringResource(R.string.history_status_confirmed)
        "PENDING" -> stringResource(R.string.history_status_pending)
        else -> status
    }
