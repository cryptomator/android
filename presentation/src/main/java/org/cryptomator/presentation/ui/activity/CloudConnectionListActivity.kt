package org.cryptomator.presentation.ui.activity

import androidx.fragment.app.Fragment
import org.cryptomator.domain.Vault
import org.cryptomator.generator.Activity
import org.cryptomator.generator.InjectIntent
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.ActivityLayoutBinding
import org.cryptomator.presentation.intent.CloudConnectionListIntent
import org.cryptomator.presentation.model.CloudModel
import org.cryptomator.presentation.presenter.CloudConnectionListPresenter
import org.cryptomator.presentation.ui.activity.view.CloudConnectionListView
import org.cryptomator.presentation.ui.bottomsheet.CloudConnectionSettingsBottomSheet
import org.cryptomator.presentation.ui.dialog.DeleteCloudConnectionWithVaultsDialog
import org.cryptomator.presentation.ui.dialog.PCloudCredentialsUpdatedDialog
import org.cryptomator.presentation.ui.fragment.CloudConnectionListFragment
import javax.inject.Inject

@Activity
class CloudConnectionListActivity : BaseActivity<ActivityLayoutBinding>(ActivityLayoutBinding::inflate),
	CloudConnectionListView,
	CloudConnectionSettingsBottomSheet.Callback,
	DeleteCloudConnectionWithVaultsDialog.Callback,
	PCloudCredentialsUpdatedDialog.Callback {

	@Inject
	lateinit var presenter: CloudConnectionListPresenter

	@InjectIntent
	lateinit var cloudConnectionListIntent: CloudConnectionListIntent

	override val isFinishOnNodeClicked: Boolean
		get() = cloudConnectionListIntent.finishOnCloudItemClick()

	override fun setupView() {
		binding.mtToolbar.toolbar.title = cloudConnectionListIntent.dialogTitle()
		setSupportActionBar(binding.mtToolbar.toolbar)
	}

	override fun setupPresenter() {
		presenter.setSelectedCloudType(cloudConnectionListIntent.cloudType())
	}

	override fun onStart() {
		super.onStart()
		connectionListFragment().setSelectedCloudType(cloudConnectionListIntent.cloudType())
	}

	override fun showCloudModels(cloudNodes: List<CloudModel>) {
		connectionListFragment().show(cloudNodes)
	}

	private fun connectionListFragment(): CloudConnectionListFragment = getCurrentFragment(R.id.fragment_container) as CloudConnectionListFragment

	override fun createFragment(): Fragment = CloudConnectionListFragment()

	override fun showNodeSettings(cloudModel: CloudModel) {
		val cloudNodeSettingDialog = //
			CloudConnectionSettingsBottomSheet.newInstance(cloudModel)
		cloudNodeSettingDialog.show(supportFragmentManager, "CloudNodeSettings")
	}

	override fun showCloudConnectionHasVaultsDialog(cloudModel: CloudModel, vaultsOfCloud: ArrayList<Vault>) {
		showDialog(DeleteCloudConnectionWithVaultsDialog.newInstance(cloudModel, vaultsOfCloud))
	}

	override fun onChangeCloudClicked(cloudModel: CloudModel) {
		presenter.onChangeCloudClicked(cloudModel)
	}

	override fun onDeleteCloudClicked(cloudModel: CloudModel) {
		presenter.onDeleteCloudClicked(cloudModel)
	}

	override fun onDeleteCloudConnectionAndVaults(cloudModel: CloudModel, vaultsOfCloud: ArrayList<Vault>) {
		presenter.onDeleteCloudConnectionAndVaults(cloudModel, vaultsOfCloud)
	}

	override fun onNotifyForPCloudCredentialsUpdateFinished() {
		// nothing to do here
	}
}
