package org.cryptomator.data.cloud.smb

import com.hierynomus.mserref.NtStatus
import com.hierynomus.mssmb2.SMB2MessageCommandCode
import com.hierynomus.mssmb2.SMBApiException
import org.cryptomator.util.ExceptionUtil

/**
 * @return whether [e] was caused by an SMB error response with one of the given [statuses]
 */
internal fun hasStatus(e: Throwable, vararg statuses: NtStatus): Boolean {
	return apiException(e)?.let { apiException -> statuses.any { it.value == apiException.statusCode } } ?: false
}

/**
 * @return the request the server answered with the error [e] was caused by, if any
 */
internal fun failedCommand(e: Throwable): SMB2MessageCommandCode? {
	return apiException(e)?.failedCommand
}

private fun apiException(e: Throwable): SMBApiException? {
	return ExceptionUtil.extract(e, SMBApiException::class.java).orElse(null)
}
