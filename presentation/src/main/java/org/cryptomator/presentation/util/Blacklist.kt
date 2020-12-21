package org.cryptomator.presentation.util

import org.cryptomator.presentation.model.CloudNodeModel

internal interface Blacklist<T : CloudNodeModel<*>> {

	fun isBlacklisted(cloudNodeModel: T): Boolean

	interface Entry {
		fun isBlacklisted(name: String): Boolean
	}

}
