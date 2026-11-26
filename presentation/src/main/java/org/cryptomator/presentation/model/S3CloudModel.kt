package org.cryptomator.presentation.model

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.S3Cloud
import org.cryptomator.domain.S3Provider

class S3CloudModel(cloud: Cloud) : CloudModel(cloud) {

	override fun name(): Int {
		return cloudType().displayNameResource
	}

	override fun username(): String {
		return cloud().displayName()
	}

	override fun cloudType(): CloudTypeModel {
		return CloudTypeModel.valueOf(toCloud())
	}

	fun id(): Long? {
		return cloud().id()
	}

	fun accessKey(): String {
		return cloud().accessKey()
	}

	fun secretKey(): String {
		return cloud().secretKey()
	}

	fun s3Bucket(): String {
		return cloud().s3Bucket()
	}

	fun s3Endpoint(): String {
		return cloud().s3Endpoint()
	}

	fun s3Region(): String {
		return cloud().s3Region()
	}

	fun provider(): S3Provider? {
		return cloud().provider()
	}

	private fun cloud(): S3Cloud {
		return toCloud() as S3Cloud
	}
}
