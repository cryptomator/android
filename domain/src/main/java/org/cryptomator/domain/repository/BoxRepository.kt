package org.cryptomator.domain.repository

import org.cryptomator.domain.BoxCloud
import org.cryptomator.domain.exception.BackendException

interface BoxRepository {

	/**
	 * Exchanges the authorization code of the OAuth redirect for tokens and stores them for the signed-in account, adding a cloud for it
	 * unless one exists already. Returns the stored cloud.
	 */
	@Throws(BackendException::class)
	fun authenticate(authorizationCode: String): BoxCloud

	/**
	 * Like [authenticate] but only for the account of the given cloud. Throws [org.cryptomator.domain.exception.authentication.WrongCredentialsException]
	 * without storing anything if another account signed in.
	 */
	@Throws(BackendException::class)
	fun reauthenticate(cloud: BoxCloud, authorizationCode: String): BoxCloud

}
