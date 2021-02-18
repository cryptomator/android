package org.cryptomator.presentation.ui.activity.view

import org.cryptomator.domain.Vault
import org.cryptomator.presentation.model.CloudModel
import java.util.ArrayList

interface CloudConnectionListView : View {

	val isFinishOnNodeClicked: Boolean

	fun showCloudModels(cloudNodes: List<CloudModel>)
	fun showNodeSettings(cloudModel: CloudModel)
	fun showCloudConnectionHasVaultsDialog(cloudModel: CloudModel, vaultsOfCloud: ArrayList<Vault>)

}
