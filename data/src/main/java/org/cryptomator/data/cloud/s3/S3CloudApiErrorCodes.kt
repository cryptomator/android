package org.cryptomator.data.cloud.s3

enum class S3CloudApiErrorCodes(val value: String) {
	ACCESS_DENIED("AccessDenied"), //
	ACCOUNT_PROBLEM("AccountProblem"), //
	INTERNAL_ERROR("InternalError"), //
	INVALID_ACCESS_KEY_ID("InvalidAccessKeyId"), //
	INVALID_BUCKET_NAME("InvalidBucketName"), //
	INVALID_OBJECT_STATE("InvalidObjectState"), //
	NO_SUCH_BUCKET("NoSuchBucket"), //
	NO_SUCH_KEY("NoSuchKey")
}
