package com.tpov.schoolquiz.shared.core.sync

interface Syncable {
    suspend fun sync(): Result<Unit>
}
