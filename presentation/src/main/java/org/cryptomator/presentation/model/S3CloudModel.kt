package org.cryptomator.presentation.model

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.S3Cloud
import org.cryptomator.presentation.R

class S3CloudModel(cloud: Cloud) : CloudModel(cloud) {

	override fun name(): Int {
		return R.string.cloud_names_s3
	}

	override fun username(): String {
		return cloud().displayName()
	}

	override fun cloudType(): CloudTypeModel {
		return CloudTypeModel.S3
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

	private fun cloud(): S3Cloud {
		return toCloud() as S3Cloud
	}
}
