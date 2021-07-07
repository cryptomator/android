package org.cryptomator.presentation.ui.activity

import org.cryptomator.generator.Activity
import org.cryptomator.generator.InjectIntent
import org.cryptomator.presentation.R
import org.cryptomator.presentation.intent.EmptyDirIdFileInfoIntent
import org.cryptomator.presentation.presenter.EmptyDirIdFileInfoPresenter
import org.cryptomator.presentation.ui.activity.view.EmptyDirFileView
import org.cryptomator.presentation.ui.fragment.EmptyDirIdFileInfoFragment
import javax.inject.Inject
import kotlinx.android.synthetic.main.toolbar_layout.toolbar

@Activity(layout = R.layout.activity_empty_dir_file_info)
class EmptyDirIdFileInfoActivity : BaseActivity(), EmptyDirFileView {

	@Inject
	lateinit var presenter: EmptyDirIdFileInfoPresenter

	@InjectIntent
	lateinit var emptyDirIdFileInfoIntent: EmptyDirIdFileInfoIntent

	override fun setupView() {
		setupToolbar()
	}

	private fun setupToolbar() {
		toolbar.title = getString(
			R.string.screen_empty_dir_file_info_title,
			emptyDirIdFileInfoIntent.dirName()
		)
		setSupportActionBar(toolbar)
	}

	override fun onResume() {
		super.onResume()
		(supportFragmentManager.findFragmentByTag("EmptyDirIdFileInfoFragment") as EmptyDirIdFileInfoFragment)
			.setDirFilePath(emptyDirIdFileInfoIntent.dirFilePath())
	}
}
