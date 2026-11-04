package org.cryptomator.presentation.ui.activity.view

interface SettingsView : View {

	fun disableAutoUpload()
	fun refreshUpdateTimeView()
	fun showCacheSize(size: Long)

}
