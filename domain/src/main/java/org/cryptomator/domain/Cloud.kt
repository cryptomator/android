package org.cryptomator.domain

import java.io.Serializable

interface Cloud : Serializable {

	fun id(): Long?
	fun type(): CloudType
	fun configurationMatches(cloud: Cloud?): Boolean
	fun persistent(): Boolean
	fun requiresNetwork(): Boolean
	fun isReadOnly(): Boolean //TODO Implement read-only checks
}
