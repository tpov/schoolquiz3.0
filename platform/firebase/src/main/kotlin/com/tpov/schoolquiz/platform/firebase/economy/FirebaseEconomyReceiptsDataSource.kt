package com.tpov.schoolquiz.platform.firebase.economy

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.tpov.schoolquiz.shared.feature.economy.data.remote.EconomyReceiptsRemoteDataSource
import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyReceipt
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Живое чтение квитанций владельца (история 5.4).
 *
 * Правила разрешают игроку читать только свои `users/{uid}/receipts` — это и есть доставка:
 * начисление приезжает строкой, пока экран открыт, без серверного вью к той же двери. Свежие
 * первыми; гость получает пустой список — начислений без аккаунта не бывает.
 */
class FirebaseEconomyReceiptsDataSource(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : EconomyReceiptsRemoteDataSource {
    override fun observeReceipts(limit: Int): Flow<List<EconomyReceipt>> =
        callbackFlow {
            val uid = auth.currentUser?.uid
            if (uid == null) {
                trySend(emptyList())
                close()
                return@callbackFlow
            }
            val listener = firestore.collection("users").document(uid)
                .collection("receipts")
                .orderBy("createdAtMs", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    // Квитанции пишет сервер, и кривой документ — серверный баг: падаем громко,
                    // а не молча пропускаем (прецедент приватных отчётов во вью inbox).
                    trySend(snapshot?.documents?.map(::receiptFromSnapshot) ?: emptyList())
                }
            awaitClose { listener.remove() }
        }
}

/** Отображение документа квитанции: поля сервера читаются строгой формой, параметры — строками. */
internal fun receiptFromSnapshot(snapshot: DocumentSnapshot): EconomyReceipt =
    receiptFrom(id = snapshot.id, data = snapshot.data)

internal fun receiptFrom(
    id: String,
    data: Map<String, Any>?,
): EconomyReceipt {
    require(id.isNotBlank()) { "receipt document id is blank" }
    requireNotNull(data) { "receipt $id has no data" }
    return EconomyReceipt(
        id = id,
        kind = textField(data, "kind", id),
        messageKey = textField(data, "messageKey", id),
        params = stringParams(data["params"], id),
        amount = longFieldOr(data["amount"], "amount", id),
        currency = textField(data, "currency", id),
        createdAtMs = longFieldOr(data["createdAtMs"], "createdAtMs", id),
    )
}

private fun textField(data: Map<String, Any>, key: String, id: String): String =
    (data[key] as? String)?.takeIf { it.isNotBlank() }
        ?: error("receipt $id has a malformed $key")

private fun longFieldOr(value: Any?, key: String, id: String): Long = when (value) {
    is Long -> value
    is Int -> value.toLong()
    is Number -> value.toLong()
    else -> error("receipt $id has a malformed $key")
}

private fun stringParams(value: Any?, id: String): Map<String, String> {
    val raw = value as? Map<*, *> ?: error("receipt $id has malformed params")
    return raw.entries.associate { (key, text) ->
        val name = key as? String ?: error("receipt $id has a malformed param name")
        name to when (text) {
            is String -> text
            is Number -> text.toString()
            is Boolean -> text.toString()
            else -> error("receipt $id has a malformed param $name")
        }
    }
}
