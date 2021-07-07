package org.cryptomator.domain

import java.io.Serializable

interface CloudNode : Serializable {

	val cloud: Cloud?
	val name: String
	val path: String
	val parent: CloudFolder?
}
