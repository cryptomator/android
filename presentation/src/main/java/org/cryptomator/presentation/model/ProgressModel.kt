package org.cryptomator.presentation.model

import java.io.Serializable

class ProgressModel constructor(private val state: ProgressStateModel, private val percentage: Int = UNKNOWN_PROGRESS_PERCENTAGE) : Serializable {

	fun progress(): Int {
		return percentage
	}

	fun state(): ProgressStateModel {
		return state
	}

	companion object {
		@JvmField
		val GENERIC = ProgressModel(ProgressStateModel.UNKNOWN)

		@JvmField
		val COMPLETED = ProgressModel(ProgressStateModel.COMPLETED)
		const val UNKNOWN_PROGRESS_PERCENTAGE = -1
	}
}
