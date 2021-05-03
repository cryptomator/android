package org.cryptomator.presentation.ui.activity

import androidx.fragment.app.Fragment
import org.cryptomator.domain.Vault
import org.cryptomator.generator.Activity
import org.cryptomator.generator.InjectIntent
import org.cryptomator.presentation.R
import org.cryptomator.presentation.intent.CloudConnectionListIntent
import org.cryptomator.presentation.model.CloudModel
import org.cryptomator.presentation.presenter.CloudConnectionListPresenter
import org.cryptomator.presentation.ui.activity.view.CloudConnectionListView
import org.cryptomator.presentation.ui.bottomsheet.CloudConnectionSettingsBottomSheet
import org.cryptomator.presentation.ui.dialog.DeleteCloudConnectionWithVaultsDialog
import org.cryptomator.presentation.ui.fragment.CloudConnectionListFragment
import java.util.ArrayList
import javax.inject.Inject
import kotlinx.android.synthetic.main.toolbar_layout.toolbar

@Activity
class CloudConnectionListActivity : BaseActivity(),
		CloudConnectionListView,
		CloudConnectionSettingsBottomSheet.Callback,
		DeleteCloudConnectionWithVaultsDialog.Callback {

	@Inject
	lateinit var presenter: CloudConnectionListPresenter

	@InjectIntent
	lateinit var cloudConnectionListIntent: CloudConnectionListIntent

	override val isFinishOnNodeClicked: Boolean
		get() = cloudConnectionListIntent.finishOnCloudItemClick()

	override fun setupView() {
		toolbar.title = cloudConnectionListIntent.dialogTitle()
		setSupportActionBar(toolbar)
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

	private fun connectionListFragment(): CloudConnectionListFragment = getCurrentFragment(R.id.fragmentContainer) as CloudConnectionListFragment

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
}
