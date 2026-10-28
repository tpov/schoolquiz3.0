package com.tpov.schoolquiz.android.feature.exam.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirButton
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirOutline
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirS2
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT2
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType

/*
 * NOIR (эпик 9.6): token-переодевка экранов сертификатов. Плейсхолдеры CertificateText.* до
 * подтверждения владельцем не тронуты; структура состояний — последнего заработанного, ожидания
 * подписи, статуса верификации — прежняя.
 */

internal object CertificateText {
    fun pendingSigning(): String = "CERT_SIGNING_PENDING"
    fun none(): String = "CERT_NONE"
    fun loadFailed(): String = "CERT_LOAD_FAILED"
    fun verifyUnavailable(): String = "CERT_VERIFY_UNAVAILABLE"
    fun percent(percent: Int): String = "CERT_PERCENT_$percent"
    fun validity(from: Long, to: Long): String = "CERT_VALIDITY_${from}_$to"
    fun verifyHint(): String = "CERT_VERIFY_HINT"
    fun status(status: com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ExamVerifyStatus): String =
        "CERT_STATUS_$status"
}

@Composable
internal fun CertificateListContent(view: CertificateView, onVerify: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (view) {
            CertificateView.Loading -> CircularProgressIndicator(color = LocalNoirAccent.current)
            // Сбой транспорта — отдельное состояние, не «нет сертификатов» и не NOT_FOUND.
            CertificateView.LoadFailed -> Text(text = CertificateText.loadFailed(), style = NoirType.rowSub)
            is CertificateView.VerifyUnavailable ->
                Text(text = CertificateText.verifyUnavailable(), style = NoirType.rowSub)
            CertificateView.None -> Text(text = CertificateText.none(), style = NoirType.rowSub)
            is CertificateView.Earned -> {
                Text(text = CertificateText.percent(view.percent), style = NoirType.price)
                Text(text = CertificateText.validity(view.issuedAtMs, view.expiresAtMs), style = NoirType.rowSub)
                // Ожидание подписи показывается явно: неподписанная публикация невозможна.
                if (view.signingPending) Text(text = CertificateText.pendingSigning(), style = NoirType.rowSub)
                VerifyEntry(issuanceId = view.issuanceId, onVerify = onVerify)
            }
            is CertificateView.Verified -> {
                Text(text = CertificateText.status(view.status), style = NoirType.rowTitle)
                view.snapshotJson?.let { Text(text = it, style = NoirType.rowSub) }
            }
        }
    }
}

@Composable
private fun VerifyEntry(issuanceId: String, onVerify: (String) -> Unit) {
    var input by remember { mutableStateOf(issuanceId) }
    OutlinedTextField(
        value = input,
        onValueChange = { input = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(text = CertificateText.verifyHint(), style = NoirType.rowSub) },
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedTextColor = NoirT1,
                unfocusedTextColor = NoirT1,
                focusedContainerColor = NoirS2,
                unfocusedContainerColor = NoirS2,
                focusedBorderColor = LocalNoirAccent.current,
                unfocusedBorderColor = NoirOutline,
                focusedLabelColor = LocalNoirAccent.current,
                unfocusedLabelColor = NoirT2,
                cursorColor = LocalNoirAccent.current,
            ),
    )
    NoirButton(text = CertificateText.verifyHint(), onClick = { onVerify(input) })
}
