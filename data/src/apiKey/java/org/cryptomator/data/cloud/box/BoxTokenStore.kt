package org.cryptomator.data.cloud.box

import android.content.Context
import com.google.gson.annotations.SerializedName
import org.cryptomator.data.db.dao.CloudDao
import org.cryptomator.domain.BoxCloud
import org.cryptomator.domain.exception.authentication.NoAuthenticationProvidedException
import org.cryptomator.util.crypto.CredentialCryptor
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

internal class BoxTokens(
	@SerializedName("access_token") val accessToken: String,
	@SerializedName("refresh_token") val refreshToken: String,
	@SerializedName("expires_at") val expiresAt: Long
) {

	fun expiresSoon(): Boolean {
		return System.currentTimeMillis() > expiresAt - EXPIRY_MARGIN_MILLIS
	}

	companion object {

		private const val EXPIRY_MARGIN_MILLIS = 60_000L
	}
}

/**
 * Owns the tokens of all Box connections, which live in the database only. Box refresh tokens are single-use, so refreshed tokens are
 * stored right away and refreshing is serialized per connection across all of its repositories.
 */
@Singleton
class BoxTokenStore @Inject constructor(private val context: Context, private val cloudDao: CloudDao) {

	private val locks = ConcurrentHashMap<Long, Any>()

	internal fun load(cloud: BoxCloud): BoxTokens {
		val encrypted = cloud.id()?.let { cloudDao.load(it)?.accessToken } ?: throw NoAuthenticationProvidedException(cloud)
		return decrypt(encrypted)
	}

	internal fun store(cloud: BoxCloud, tokens: BoxTokens) {
		val id = cloud.id() ?: throw IllegalArgumentException("Tokens can only be stored for a saved cloud")
		cloudDao.updateAccessToken(id, encrypt(tokens))
	}

	@Throws(IOException::class)
	internal fun refresh(cloud: BoxCloud, rejected: BoxTokens, oAuth: BoxOAuth): BoxTokens {
		val id = cloud.id() ?: throw NoAuthenticationProvidedException(cloud)
		synchronized(locks.getOrPut(id) { Any() }) {
			val current = load(cloud)
			if (current.accessToken != rejected.accessToken) {
				return current // already refreshed by another repository of this connection
			}
			return oAuth.refresh(current.refreshToken).also { store(cloud, it) }
		}
	}

	private fun encrypt(tokens: BoxTokens): String {
		return CredentialCryptor.getInstance(context).encrypt(BoxHttpClient.gson.toJson(tokens))
	}

	private fun decrypt(encrypted: String): BoxTokens {
		return BoxHttpClient.gson.fromJson(CredentialCryptor.getInstance(context).decrypt(encrypted), BoxTokens::class.java)
	}
}
