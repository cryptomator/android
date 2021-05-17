package org.cryptomator.data.cloud.s3;

public enum S3CloudApiErrorCodes {
	ACCESS_DENIED("AccessDenied"), ACCOUNT_PROBLEM("AccountProblem"), INTERNAL_ERROR("InternalError"), INVALID_ACCESS_KEY_ID("InvalidAccessKeyId"), INVALID_BUCKET_NAME("InvalidBucketName"), INVALID_OBJECT_STATE("InvalidObjectState"), NO_SUCH_BUCKET("NoSuchBucket"), NO_SUCH_KEY("NoSuchKey");

	private final String value;

	S3CloudApiErrorCodes(final String newValue) {
		value = newValue;
	}

	public String getValue() {
		return value;
	}
}
