package org.cryptomator.presentation.ui.activity.view

import org.cryptomator.presentation.model.CloudModel

interface CloudSettingsView : View {

	fun render(cloudModels: List<CloudModel>)
	fun update(cloud: CloudModel)

}
