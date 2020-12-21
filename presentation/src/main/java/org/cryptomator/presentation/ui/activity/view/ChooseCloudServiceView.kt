package org.cryptomator.presentation.ui.activity.view

import org.cryptomator.presentation.model.CloudTypeModel

interface ChooseCloudServiceView : View {

	fun render(cloudModels: List<CloudTypeModel>)

}
