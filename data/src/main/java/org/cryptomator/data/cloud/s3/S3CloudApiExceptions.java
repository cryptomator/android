package org.cryptomator.data.cloud.s3;

public class S3CloudApiExceptions {

	public enum S3CloudApiErrorCodes {
		NO_SUCH_BUCKET("NoSuchBucket");

		private final String value;

		S3CloudApiErrorCodes(final String newValue) {
			value = newValue;
		}

		public String getValue() {
			return value;
		}
	}

}
