package org.cryptomator.presentation.ui.activity.view

interface S3AddOrChangeView : View {

	fun onCheckUserInputSucceeded(accessKey: String, secretKey: String, bucket: String, endpoint: String?, region: String?, cloudId: Long?, displayName: String)

}
