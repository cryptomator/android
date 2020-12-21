package org.cryptomator.presentation.ui.activity

import org.cryptomator.presentation.model.ProgressModel

interface ProgressAware {

	fun showProgress(progress: ProgressModel)

}
