package org.cryptomator.presentation.service

import androidx.annotation.StringRes
import org.cryptomator.presentation.R

enum class UploadAdmission(@StringRes val refusalMessage: Int?) {

	ACCEPTED(null),

	/**
	 * The vault has an upload running, queued or waiting to be resumed.
	 */
	BUSY(R.string.error_upload_vault_busy),

	UNAVAILABLE(R.string.error_upload_service_unavailable)
}
