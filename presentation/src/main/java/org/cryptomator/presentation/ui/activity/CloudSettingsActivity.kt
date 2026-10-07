package org.cryptomator.presentation.ui.activity

import androidx.fragment.app.Fragment
import org.cryptomator.generator.Activity
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.ActivityLayoutBinding
import org.cryptomator.presentation.model.CloudModel
import org.cryptomator.presentation.presenter.CloudSettingsPresenter
import org.cryptomator.presentation.ui.activity.view.CloudSettingsView
import org.cryptomator.presentation.ui.fragment.CloudSettingsFragment
import javax.inject.Inject

@Activity
class CloudSettingsActivity : BaseActivity<ActivityLayoutBinding>(ActivityLayoutBinding::inflate), CloudSettingsView {

	@Inject
	lateinit var cloudSettingsPresenter: CloudSettingsPresenter

	override fun setupView() {
		binding.mtToolbar.toolbar.setTitle(R.string.screen_cloud_settings_title)
		setSupportActionBar(binding.mtToolbar.toolbar)
	}

	override fun createFragment(): Fragment = CloudSettingsFragment()

	override fun render(cloudModels: List<CloudModel>) {
		cloudSettingsFragment().showClouds(cloudModels)
	}

	override fun update(cloud: CloudModel) {
		cloudSettingsFragment().update(cloud)
	}

	private fun cloudSettingsFragment(): CloudSettingsFragment = getCurrentFragment(R.id.fragment_container) as CloudSettingsFragment
}
