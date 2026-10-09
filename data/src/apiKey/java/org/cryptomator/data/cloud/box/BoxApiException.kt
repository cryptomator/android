package org.cryptomator.data.cloud.box

import org.cryptomator.domain.exception.FatalBackendException

internal class BoxApiException(val statusCode: Int, val code: String?, message: String?, val conflict: BoxItem? = null) : FatalBackendException("Box API responded with $statusCode ($code): $message") {

	val isAuthenticationError: Boolean
		get() = statusCode == 401 || code == "invalid_grant"

	val isNotFound: Boolean
		get() = statusCode == 404

	val isConflict: Boolean
		get() = statusCode == 409
}
