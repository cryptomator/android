package org.cryptomator.data.cloud.s3

object S3CloudApiExceptions {

	@JvmStatic
	fun isAccessProblem(errorCode: String): Boolean {
		return errorCode == S3CloudApiErrorCodes.ACCESS_DENIED.value //
				|| errorCode == S3CloudApiErrorCodes.ACCOUNT_PROBLEM.value //
				|| errorCode == S3CloudApiErrorCodes.INVALID_ACCESS_KEY_ID.value
	}

	@JvmStatic
	fun isNoSuchBucketException(errorCode: String): Boolean {
		return errorCode == S3CloudApiErrorCodes.NO_SUCH_BUCKET.value
	}
}
