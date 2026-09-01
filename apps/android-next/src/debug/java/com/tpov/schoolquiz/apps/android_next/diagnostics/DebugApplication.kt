package com.tpov.schoolquiz.apps.android_next.diagnostics

import com.tpov.schoolquiz.apps.android_next.AppApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * The debug build's application: everything the real one does, plus the diagnostics.
 *
 * Declared only in the debug manifest, so neither this class nor anything it reaches is compiled
 * into a release APK. It exists because the boundary check has to run *after* Koin is started —
 * a ContentProvider, the usual place for debug-only startup work, is created before
 * `Application.onCreate` and would find no dependency graph to read the database from.
 */
class DebugApplication : AppApplication() {
    private val diagnosticsScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val koin = GlobalContext.get()
        diagnosticsScope.launch {
            RebuildBoundaryCheck(koin.get(), koin.get(), koin.get()).logReport()
        }
    }
}
