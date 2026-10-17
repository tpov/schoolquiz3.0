@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.tpov.schoolquiz.android.feature.tournament.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGroup
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT3
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.tournament.presentation.R
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentAdmissionComponent

/**
 * Маршрут допуска (история 5.10): запись скачивания с серверным флагом повтора, честная витрина
 * окна события по серверному «сейчас» и переход к участию. Решение о допуске — только сервер.
 */
@Composable
fun TournamentAdmissionScreen(
    component: TournamentAdmissionComponent,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by component.state.collectAsStateWithLifecycle(component.state.value)
    val backLabel = stringResource(R.string.tournament_accessibility_back)
    val continueLabel = stringResource(R.string.tournament_accessibility_continue)
    val event = state.event
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.tournament_admission_title), style = NoirType.groupTitle)
        if (event == null) {
            Text(stringResource(R.string.tournament_empty), color = NoirT3)
            OutlinedButton(
                onClick = {
                    onBack()
                    component.onClose()
                },
                modifier =
                    Modifier.testTag("tournament_admission_back").semantics { contentDescription = backLabel },
            ) {
                Text(stringResource(R.string.tournament_back))
            }
            return@Column
        }
        Text(statusText(event), color = NoirT3)
        if (state.outsideWindow) {
            Text(
                stringResource(R.string.tournament_admission_outside_window),
                Modifier.testTag("tournament_admission_outside_window"),
                color = NoirT3,
            )
        }
        if (state.downloadFitsEvent) {
            Button(
                onClick = component::onRecordDownloadClick,
                enabled = !state.busy,
                modifier = Modifier.testTag("tournament_admission_download"),
            ) {
                Text(stringResource(R.string.tournament_admission_download))
            }
        }
        state.download?.let { download ->
            Text(
                if (download.replayed) {
                    stringResource(R.string.tournament_admission_download_replayed)
                } else {
                    stringResource(R.string.tournament_admission_download_first)
                },
                Modifier.testTag("tournament_admission_download_state"),
                color = NoirT3,
            )
        }
        Text(stringResource(R.string.tournament_admission_link_note), color = NoirT3)
        if (state.busy) CircularProgressIndicator()
        Text(noticeText(state.notice), color = NoirT3)
        NoirGroup {
            Column(
                modifier = Modifier.padding(12.dp).testTag("tournament_admission_continue_group"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        component.onContinueClick()
                        onContinue()
                    },
                    modifier =
                        Modifier.testTag("tournament_admission_continue").semantics {
                            contentDescription = continueLabel
                        },
                ) {
                    Text(stringResource(R.string.tournament_admission_to_participation))
                }
                OutlinedButton(
                    onClick = {
                        component.onClose()
                        onBack()
                    },
                ) {
                    Text(stringResource(R.string.tournament_back))
                }
            }
        }
    }
}
