package org.cryptomator.domain

import java.util.Date

interface CloudFile : CloudNode {

	val size: Long?
	val modified: Date?
	override val parent: CloudFolder
}
