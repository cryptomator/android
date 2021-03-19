package org.cryptomator.data.cloud.pcloud;

public class PCloudApiError {

	public enum PCloudApiErrorCodes {
		LOGIN_REQUIRED(1000),
		NO_FULL_PATH_OR_NAME_FOLDER_ID_PROVIDED(1001),
		NO_FULL_PATH_OR_FOLDER_ID_PROVIDED(1002),
		NO_FILE_ID_OR_PATH_PROVIDED(1004),
		INVALID_DATE_TIME_FORMAT(1013),
		NO_DESTINATION_PROVIDED(1016),
		INVALID_FOLDER_ID(1017),
		INVALID_DESTINATION(1037),
		PROVIDE_URL(1040),
		UPLOAD_NOT_FOUND(1900),
		TRANSFER_NOT_FOUND(1902),
		LOGIN_FAILED(2000),
		INVALID_FILE_OR_FOLDER_NAME(2001),
		COMPONENT_OF_PARENT_DIRECTORY_DOES_NOT_EXIST(2002),
		ACCESS_DENIED(2003),
		FILE_OR_FOLDER_ALREADY_EXISTS(2004),
		DIRECTORY_DOES_NOT_EXIST(2005),
		FOLDER_NOT_EMPTY(2006),
		CANNOT_DELETE_ROOT_FOLDER(2007),
		USER_OVER_QUOTA(2008),
		FILE_NOT_FOUND(2009),
		INVALID_PATH(2010),
		SHARED_FOLDER_IN_SHARED_FOLDER(2023),
		ACTIVE_SHARES_OR_SHAREREQUESTS_PRESENT(2028),
		CONNECTION_BROKE(2041),
		CANNOT_RENAME_ROOT_FOLDER(2042),
		CANNOT_MOVE_FOLDER_INTO_SUBFOLDER_OF_ITSELF(2043),
		FILE_OR_FOLDER_NOT_FOUND(2055),
		NO_FILE_UPLOAD_DETECTED(2088),
		INVALID_ACCESS_TOKEN(2094),
		ACCESS_TOKEN_REVOKED(2095),
		TRANSFER_OVER_QUOTA(2097),
		TARGET_FOLDER_DOES_NOT_EXIST(2208),
		TOO_MANY_LOGIN_TRIES_FROM_IP(4000),
		INTERNAL_ERROR(5000),
		INTERNAL_UPLOAD_ERROR(5001);

		private final int value;

		PCloudApiErrorCodes(final int newValue) {
			value = newValue;
		}

		public int getValue() {
			return value;
		}
	}

	public static boolean isCloudNodeAlreadyExistsException(int errorCode) {
		return errorCode == PCloudApiErrorCodes.FILE_OR_FOLDER_ALREADY_EXISTS.getValue();
	}

	public static boolean isFatalBackendException(int errorCode) {
		return errorCode == PCloudApiErrorCodes.INTERNAL_UPLOAD_ERROR.getValue()
				|| errorCode == PCloudApiErrorCodes.INTERNAL_UPLOAD_ERROR.getValue()
				|| errorCode == PCloudApiErrorCodes.UPLOAD_NOT_FOUND.getValue()
				|| errorCode == PCloudApiErrorCodes.TRANSFER_NOT_FOUND.getValue();
	}

	public static boolean isForbiddenException(int errorCode) {
		return errorCode == PCloudApiErrorCodes.ACCESS_DENIED.getValue();
	}

	public static boolean isNetworkConnectionException(int errorCode) {
		return errorCode == PCloudApiErrorCodes.CONNECTION_BROKE.getValue();
	}

	public static boolean isNoSuchCloudFileException(int errorCode) {
		return errorCode == PCloudApiErrorCodes.FILE_NOT_FOUND.getValue()
				|| errorCode == PCloudApiErrorCodes.FILE_OR_FOLDER_NOT_FOUND.getValue()
				|| errorCode == PCloudApiErrorCodes.DIRECTORY_DOES_NOT_EXIST.getValue();
	}

	public static boolean isWrongCredentialsException(int errorCode) {
		return errorCode == PCloudApiErrorCodes.INVALID_ACCESS_TOKEN.getValue()
				|| errorCode == PCloudApiErrorCodes.ACCESS_TOKEN_REVOKED.getValue();
	}

	public static boolean isUnauthorizedException(int errorCode) {
		return errorCode == PCloudApiErrorCodes.LOGIN_FAILED.getValue()
				|| errorCode == PCloudApiErrorCodes.LOGIN_REQUIRED.getValue()
				|| errorCode == PCloudApiErrorCodes.TOO_MANY_LOGIN_TRIES_FROM_IP.getValue();
	}

}