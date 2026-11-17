package org.cryptomator.data.repository

import android.content.Context
import org.cryptomator.data.cloud.box.BoxApi
import org.cryptomator.data.cloud.box.BoxHttpClient
import org.cryptomator.data.cloud.box.BoxOAuth
import org.cryptomator.data.cloud.box.BoxTokenStore
import org.cryptomator.data.cloud.box.BoxTokens
import org.cryptomator.data.cloud.box.BoxUser
import org.cryptomator.domain.BoxCloud
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.exception.NetworkConnectionException
import org.cryptomator.domain.exception.authentication.WrongCredentialsException
import org.cryptomator.domain.repository.BoxRepository
import org.cryptomator.domain.repository.CloudRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BoxRepositoryImpl @Inject constructor(
	private val context: Context,
	private val cloudRepository: CloudRepository,
	private val tokenStore: BoxTokenStore
) : BoxRepository {

	@Throws(BackendException::class)
	override fun authenticate(authorizationCode: String): BoxCloud {
		val (tokens, user) = signIn(authorizationCode)
		val userId = requireUserId(user)
		val existingCloud = cloudRepository.clouds(CloudType.BOX).map { it as BoxCloud }.firstOrNull { it.userId() == userId }
		return store(existingCloud ?: BoxCloud.aBoxCloud().withUserId(userId).build(), user, tokens)
	}

	@Throws(BackendException::class)
	override fun reauthenticate(cloud: BoxCloud, authorizationCode: String): BoxCloud {
		val (tokens, user) = signIn(authorizationCode)
		if (requireUserId(user) != cloud.userId()) {
			throw WrongCredentialsException(cloud)
		}
		return store(cloud, user, tokens)
	}

	private fun signIn(authorizationCode: String): Pair<BoxTokens, BoxUser> {
		val httpClient = BoxHttpClient.create(context)
		return try {
			val tokens = BoxOAuth(httpClient).exchange(authorizationCode)
			tokens to BoxApi.currentUser(httpClient, tokens.accessToken)
		} catch (e: IOException) {
			throw NetworkConnectionException(e)
		}
	}

	private fun requireUserId(user: BoxUser): String {
		return user.id ?: throw FatalBackendException("Box account has no id")
	}

	private fun store(cloud: BoxCloud, user: BoxUser, tokens: BoxTokens): BoxCloud {
		val storedCloud = cloudRepository.store(
			BoxCloud.aCopyOf(cloud) //
				.withUsername(user.login ?: throw FatalBackendException("Box account has no login")) //
				.build()
		) as BoxCloud
		tokenStore.store(storedCloud, tokens)
		return storedCloud
	}
}
