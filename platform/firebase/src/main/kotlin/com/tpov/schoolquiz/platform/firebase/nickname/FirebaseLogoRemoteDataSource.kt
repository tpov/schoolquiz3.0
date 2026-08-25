package com.tpov.schoolquiz.platform.firebase.nickname

import com.google.firebase.functions.FirebaseFunctions
import com.tpov.schoolquiz.shared.feature.internet.profile.data.remote.LogoRemoteDataSource
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileLogo
import kotlinx.coroutines.tasks.await

/**
 * The logo shelf, over callable functions.
 *
 * Gold and the owned list have to move together, which is why buying is a call and not a write: a
 * client that could edit its own logo list could grant itself the set for nothing.
 */
class FirebaseLogoRemoteDataSource(
    private val functions: FirebaseFunctions,
) : LogoRemoteDataSource {
    override suspend fun catalog(): List<ProfileLogo> =
        call(FETCH_CATALOG, emptyMap()).list(LOGOS).map { entry ->
            ProfileLogo(
                name = entry.string(NAME) ?: "",
                price = entry.number(PRICE) ?: 0L,
                owned = entry[OWNED] as? Boolean ?: false,
            )
        }

    override suspend fun buy(logo: String): Long = call(BUY, mapOf(LOGO to logo)).number(CHARGED) ?: 0L

    private suspend fun call(
        name: String,
        payload: Map<String, Any?>,
    ): Map<*, *> {
        val result = functions.getHttpsCallable(name).call(payload).await()
        return result.getData() as? Map<*, *> ?: emptyMap<String, Any?>()
    }

    private fun Map<*, *>.string(key: String): String? = this[key] as? String

    private fun Map<*, *>.number(key: String): Long? = (this[key] as? Number)?.toLong()

    private fun Map<*, *>.list(key: String): List<Map<*, *>> =
        (this[key] as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }

    private companion object {
        const val FETCH_CATALOG = "fetchLogoCatalog"
        const val BUY = "buyLogo"
        const val LOGOS = "logos"
        const val LOGO = "logo"
        const val NAME = "name"
        const val PRICE = "price"
        const val OWNED = "owned"
        const val CHARGED = "charged"
    }
}
