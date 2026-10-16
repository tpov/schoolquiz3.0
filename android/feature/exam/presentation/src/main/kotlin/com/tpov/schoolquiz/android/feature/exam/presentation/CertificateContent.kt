package com.tpov.schoolquiz.android.feature.exam.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Экраны сертификатов (CAP-22): список профиля и публичная верификация.
 *
 * Содержимое требует отдельного подтверждения владельца (протокол §Screen-content confirmation):
 * здесь структура состояний — последний заработанный, ожидание подписи, статус верификации, —
 * а не окончательные формулировки. Тексты вынесены в [CertificateText] до подтверждения.
 */
internal object CertificateText {
    fun pendingSigning(): String = "CERT_SIGNING_PENDING"
    fun none(): String = "CERT_NONE"
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
            CertificateView.Loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            CertificateView.None -> Text(text = CertificateText.none())
            is CertificateView.Earned -> {
                Text(text = CertificateText.percent(view.percent), style = MaterialTheme.typography.headlineSmall)
                Text(text = CertificateText.validity(view.issuedAtMs, view.expiresAtMs))
                // Ожидание подписи показывается явно: неподписанная публикация невозможна.
                if (view.signingPending) Text(text = CertificateText.pendingSigning())
                VerifyEntry(issuanceId = view.issuanceId, onVerify = onVerify)
            }
            is CertificateView.Verified -> {
                Text(text = CertificateText.status(view.status))
                view.snapshotJson?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }
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
        label = { Text(text = CertificateText.verifyHint()) },
    )
    Button(onClick = { onVerify(input) }) {
        Text(text = CertificateText.verifyHint())
    }
}
