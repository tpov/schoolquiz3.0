package com.tpov.schoolquiz.android.feature.app_shell.presentation.fake

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import org.mockito.ArgumentMatchers
import org.mockito.Mockito

/**
 * Capturing fake for [WorkManager].
 *
 * [WorkManager] is abstract and cannot be extended without Android runtime,
 * so this class wraps a Mockito-created subclass and captures call arguments
 * via doAnswer stubbing. Suitable for JVM unit tests.
 *
 * Usage:
 *   val fake = FakeWorkManager()
 *   buildComponent(workManager = fake.workManager)
 *   component.onSyncNow()
 *   assertEquals(1, fake.enqueueUniqueWorkCalls)
 *   assertEquals("manual_sync", fake.lastWorkName)
 *   assertEquals(ExistingWorkPolicy.REPLACE, fake.lastPolicy)
 */
class FakeWorkManager {

    var enqueueUniqueWorkCalls: Int = 0
    var lastWorkName: String? = null
    var lastPolicy: ExistingWorkPolicy? = null

    val workManager: WorkManager = Mockito.mock(WorkManager::class.java).also { wm ->
        Mockito.doAnswer { invocation ->
            enqueueUniqueWorkCalls++
            lastWorkName = invocation.getArgument<String>(0)
            lastPolicy = invocation.getArgument<ExistingWorkPolicy>(1)
            null  // Operation return value discarded by caller — null is safe
        }.`when`(wm).enqueueUniqueWork(
            ArgumentMatchers.anyString(),
            ArgumentMatchers.any(ExistingWorkPolicy::class.java),
            ArgumentMatchers.any(OneTimeWorkRequest::class.java),
        )
    }
}
