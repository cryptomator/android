package org.cryptomator.presentation.ui.activity.view

import org.cryptomator.presentation.model.ProgressModel

interface SmbAddOrChangeView : View {

	fun onCheckUserInputSucceeded(urlPort: String, username: String, password: String, cloudId: Long?)

}
