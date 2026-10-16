package com.tpov.schoolquiz.android.feature.exam.presentation

import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ExamCertificateProfileView
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ExamCertificateRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ExamCertificateView
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ExamVerifyStatus
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ExamVerifyView
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Поверхность сертификатов: последний заработанный показывается, ожидание подписи явно. */
class CertificateListStateMachineTest {

    @Test
    fun `given no certificates then the profile shows none`() = runTest {
        val machine = CertificateListStateMachine(FakeRemote(mine = ExamCertificateProfileView(null)), this)
        machine.loadMine()
        testScheduler.advanceUntilIdle()
        assertEquals(CertificateView.None, machine.view.value)
    }

    @Test
    fun `given the latest earned issuance then pending signing stays explicit`() = runTest {
        val machine = CertificateListStateMachine(FakeRemote(mine = earned(signingState = "PENDING")), this)
        machine.loadMine()
        testScheduler.advanceUntilIdle()
        val earned = machine.view.value as CertificateView.Earned
        assertTrue("ожидание подписи — явное состояние", earned.signingPending)
        assertEquals("последний заработанный, даже с более низким счётом", 70, earned.percent)
    }

    @Test
    fun `given a signed-out verify call then the status policy renders as is`() = runTest {
        val machine =
            CertificateListStateMachine(FakeRemote(verify = ExamVerifyView(true, ExamVerifyStatus.VALID, "i1", "{}")), this)
        machine.verify("i1")
        testScheduler.advanceUntilIdle()
        val verified = machine.view.value as CertificateView.Verified
        assertEquals(ExamVerifyStatus.VALID, verified.status)
        assertEquals("{}", verified.snapshotJson)
    }

    @Test
    fun `given an expired attestation then it stays found with its own snapshot`() = runTest {
        val machine = CertificateListStateMachine(
            FakeRemote(verify = ExamVerifyView(true, ExamVerifyStatus.EXPIRED, "i0", "{}")),
            this,
        )
        machine.verify("i0")
        testScheduler.advanceUntilIdle()
        assertEquals(
            "истёкший аттестат остаётся найденным со своим снимком",
            CertificateView.Verified("i0", ExamVerifyStatus.EXPIRED, "{}"),
            machine.view.value,
        )
    }

    private fun earned(signingState: String): ExamCertificateProfileView = ExamCertificateProfileView(
        ExamCertificateView(
            issuanceId = "i2",
            courseId = "course",
            percent = 70,
            passMark = 60,
            issuedAtMs = 2_000,
            expiresAtMs = 2_000 + 365 * 24 * 3_600 * 1_000,
            signingState = signingState,
        ),
    )

    private class FakeRemote(
        private val mine: ExamCertificateProfileView = ExamCertificateProfileView(null),
        private val verify: ExamVerifyView = ExamVerifyView(false, ExamVerifyStatus.NOT_FOUND, "", null),
    ) : ExamCertificateRemoteDataSource {
        override suspend fun fetchMyCertificates(): Result<ExamCertificateProfileView> =
            Result.success(mine)

        override suspend fun publicVerify(issuanceId: String): Result<ExamVerifyView> =
            Result.success(verify)
    }
}
