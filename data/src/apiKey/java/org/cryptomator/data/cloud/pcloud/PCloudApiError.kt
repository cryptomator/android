package org.cryptomator.data.cloud.pcloud

import java.util.HashSet

object PCloudApiError {

	@JvmField
	val ignoreExistsSet = HashSet( //
		listOf( //
			PCloudApiErrorCodes.COMPONENT_OF_PARENT_DIRECTORY_DOES_NOT_EXIST.value,  //
			PCloudApiErrorCodes.FILE_NOT_FOUND.value,  //
			PCloudApiErrorCodes.FILE_OR_FOLDER_NOT_FOUND.value,  //
			PCloudApiErrorCodes.DIRECTORY_DOES_NOT_EXIST.value,  //
			PCloudApiErrorCodes.INVALID_FILE_OR_FOLDER_NAME.value //
		)
	)
	@JvmField
	val ignoreMoveSet = HashSet( //
		listOf( //
			PCloudApiErrorCodes.FILE_OR_FOLDER_ALREADY_EXISTS.value,  //
			PCloudApiErrorCodes.COMPONENT_OF_PARENT_DIRECTORY_DOES_NOT_EXIST.value,  //
			PCloudApiErrorCodes.FILE_NOT_FOUND.value,  //
			PCloudApiErrorCodes.FILE_OR_FOLDER_NOT_FOUND.value,  //
			PCloudApiErrorCodes.DIRECTORY_DOES_NOT_EXIST.value //
		) //
	)

	@JvmStatic
	fun isCloudNodeAlreadyExistsException(errorCode: Int): Boolean {
		return errorCode == PCloudApiErrorCodes.FILE_OR_FOLDER_ALREADY_EXISTS.value
	}

	fun isFatalBackendException(errorCode: Int): Boolean {
		return errorCode == PCloudApiErrorCodes.INTERNAL_UPLOAD_ERROR.value //
				|| errorCode == PCloudApiErrorCodes.INTERNAL_UPLOAD_ERROR.value //
				|| errorCode == PCloudApiErrorCodes.UPLOAD_NOT_FOUND.value //
				|| errorCode == PCloudApiErrorCodes.TRANSFER_NOT_FOUND.value
	}

	@JvmStatic
	fun isForbiddenException(errorCode: Int): Boolean {
		return errorCode == PCloudApiErrorCodes.ACCESS_DENIED.value
	}

	@JvmStatic
	fun isNetworkConnectionException(errorCode: Int): Boolean {
		return errorCode == PCloudApiErrorCodes.CONNECTION_BROKE.value
	}

	@JvmStatic
	fun isNoSuchCloudFileException(errorCode: Int): Boolean {
		return errorCode == PCloudApiErrorCodes.COMPONENT_OF_PARENT_DIRECTORY_DOES_NOT_EXIST.value //
				|| errorCode == PCloudApiErrorCodes.FILE_NOT_FOUND.value //
				|| errorCode == PCloudApiErrorCodes.FILE_OR_FOLDER_NOT_FOUND.value //
				|| errorCode == PCloudApiErrorCodes.DIRECTORY_DOES_NOT_EXIST.value
	}

	@JvmStatic
	fun isWrongCredentialsException(errorCode: Int): Boolean {
		return (errorCode == PCloudApiErrorCodes.INVALID_ACCESS_TOKEN.value //
				|| errorCode == PCloudApiErrorCodes.ACCESS_TOKEN_REVOKED.value)
	}

	@JvmStatic
	fun isUnauthorizedException(errorCode: Int): Boolean {
		return errorCode == PCloudApiErrorCodes.LOGIN_FAILED.value //
				|| errorCode == PCloudApiErrorCodes.LOGIN_REQUIRED.value //
				|| errorCode == PCloudApiErrorCodes.TOO_MANY_LOGIN_TRIES_FROM_IP.value
	}

	enum class PCloudApiErrorCodes(val value: Int) {
		LOGIN_REQUIRED(1000),  //
		NO_FULL_PATH_OR_NAME_FOLDER_ID_PROVIDED(1001),  //
		NO_FULL_PATH_OR_FOLDER_ID_PROVIDED(1002),  //
		NO_FILE_ID_OR_PATH_PROVIDED(1004),  //
		INVALID_DATE_TIME_FORMAT(1013),  //
		NO_DESTINATION_PROVIDED(1016),  //
		INVALID_FOLDER_ID(1017),  //
		INVALID_DESTINATION(1037),  //
		PROVIDE_URL(1040),  //
		UPLOAD_NOT_FOUND(1900),  //
		TRANSFER_NOT_FOUND(1902),  //
		LOGIN_FAILED(2000),  //
		INVALID_FILE_OR_FOLDER_NAME(2001),  //
		COMPONENT_OF_PARENT_DIRECTORY_DOES_NOT_EXIST(2002),  //
		ACCESS_DENIED(2003),  //
		FILE_OR_FOLDER_ALREADY_EXISTS(2004),  //
		DIRECTORY_DOES_NOT_EXIST(2005),  //
		FOLDER_NOT_EMPTY(2006),  //
		CANNOT_DELETE_ROOT_FOLDER(2007),  //
		USER_OVER_QUOTA(2008),  //
		FILE_NOT_FOUND(2009),  //
		INVALID_PATH(2010),  //
		SHARED_FOLDER_IN_SHARED_FOLDER(2023),  //
		ACTIVE_SHARES_OR_SHAREREQUESTS_PRESENT(2028),  //
		CONNECTION_BROKE(2041),  //
		CANNOT_RENAME_ROOT_FOLDER(2042),  //
		CANNOT_MOVE_FOLDER_INTO_SUBFOLDER_OF_ITSELF(2043),  //
		FILE_OR_FOLDER_NOT_FOUND(2055),  //
		NO_FILE_UPLOAD_DETECTED(2088),  //
		INVALID_ACCESS_TOKEN(2094),  //
		ACCESS_TOKEN_REVOKED(2095),  //
		TRANSFER_OVER_QUOTA(2097),  //
		TARGET_FOLDER_DOES_NOT_EXIST(2208),  //
		TOO_MANY_LOGIN_TRIES_FROM_IP(4000),  //
		INTERNAL_ERROR(5000),  //
		INTERNAL_UPLOAD_ERROR(5001);

	}
}
