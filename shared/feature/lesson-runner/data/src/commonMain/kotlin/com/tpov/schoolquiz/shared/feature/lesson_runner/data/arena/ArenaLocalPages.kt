package com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena

import com.tpov.schoolquiz.shared.core.persistence.ArenaCommandEntity
import com.tpov.schoolquiz.shared.core.persistence.ArenaTicketRecordEntity

/**
 * Opaque, local-only continuation. Distinct cursor types cannot be interchanged between lists.
 * Owner binding is not an Auth generation, launch permission, network token or a durable inventory snapshot.
 */
class ArenaLocalTicketCursor internal constructor(
    internal val ownerUid: String,
    internal val afterTicketId: String,
)

class ArenaLocalPrepareCursor internal constructor(
    internal val ownerUid: String,
    internal val afterCommandId: String,
)

/** Raw storage rows, confined to data-layer composition; a future caller must map them before exposing UI. */
data class ArenaLocalTicketPage(
    val rows: List<ArenaTicketRecordEntity>,
    val nextCursor: ArenaLocalTicketCursor?,
)

data class ArenaLocalPreparePage(
    val rows: List<ArenaCommandEntity>,
    val nextCursor: ArenaLocalPrepareCursor?,
)

internal data class ArenaLocalPageRead(
    val ownerUid: String,
    val afterId: String?,
    val pageSize: Int,
) {
    val fetchLimit: Int get() = pageSize + 1
}

internal fun localTicketPageRead(
    owner: String,
    size: Int,
    cursor: ArenaLocalTicketCursor?,
): ArenaLocalPageRead = localPageRead(owner, size, cursor?.ownerUid, cursor?.afterTicketId)

internal fun localPreparePageRead(
    owner: String,
    size: Int,
    cursor: ArenaLocalPrepareCursor?,
): ArenaLocalPageRead = localPageRead(owner, size, cursor?.ownerUid, cursor?.afterCommandId)

private fun localPageRead(
    owner: String,
    size: Int,
    cursorOwner: String?,
    after: String?,
): ArenaLocalPageRead {
    require(owner.isNotBlank())
    require(size in 1..MAX_LOCAL_PAGE_SIZE)
    require(cursorOwner == null || cursorOwner == owner) { "Local cursor belongs to another owner" }
    return ArenaLocalPageRead(owner, after, size)
}

internal fun localTicketPage(
    read: ArenaLocalPageRead,
    rows: List<ArenaTicketRecordEntity>,
): ArenaLocalTicketPage {
    check(rows.size <= read.fetchLimit)
    check(rows.all { it.ownerUid == read.ownerUid })
    val visible = rows.take(read.pageSize)
    val cursor =
        if (rows.size > read.pageSize) ArenaLocalTicketCursor(read.ownerUid, visible.last().ticketId) else null
    return ArenaLocalTicketPage(visible, cursor)
}

internal fun localPreparePage(
    read: ArenaLocalPageRead,
    rows: List<ArenaCommandEntity>,
): ArenaLocalPreparePage {
    check(rows.size <= read.fetchLimit)
    check(rows.all { it.ownerUid == read.ownerUid })
    val visible = rows.take(read.pageSize)
    val cursor =
        if (rows.size > read.pageSize) ArenaLocalPrepareCursor(read.ownerUid, visible.last().commandId) else null
    return ArenaLocalPreparePage(visible, cursor)
}

private const val MAX_LOCAL_PAGE_SIZE = 50
