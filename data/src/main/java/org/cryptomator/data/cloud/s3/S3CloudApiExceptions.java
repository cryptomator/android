package org.cryptomator.data.cloud.s3;

public class S3CloudApiExceptions {

	public static boolean isAccessProblem(String errorCode) {
		return errorCode.equals(S3CloudApiErrorCodes.ACCESS_DENIED.getValue()) || errorCode.equals(S3CloudApiErrorCodes.ACCOUNT_PROBLEM.getValue()) || errorCode.equals(S3CloudApiErrorCodes.INVALID_ACCESS_KEY_ID.getValue());
	}

	public static boolean isNoSuchBucketException(String errorCode) {
		return errorCode.equals(S3CloudApiErrorCodes.NO_SUCH_BUCKET.getValue());
	}
}
