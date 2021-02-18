package org.cryptomator.presentation.ui.fragment

import org.cryptomator.generator.Fragment
import org.cryptomator.presentation.R
import org.cryptomator.presentation.presenter.EmptyDirIdFileInfoPresenter
import javax.inject.Inject
import kotlinx.android.synthetic.main.fragment_empty_dir_file_info.infoText
import kotlinx.android.synthetic.main.fragment_empty_dir_file_info.moreDetailsButton

@Fragment(R.layout.fragment_empty_dir_file_info)
class EmptyDirIdFileInfoFragment : BaseFragment() {

	@Inject
	lateinit var presenter: EmptyDirIdFileInfoPresenter

	override fun setupView() {
		moreDetailsButton.setOnClickListener { presenter.onShowMoreInfoButtonPressed() }
	}

	fun setDirFilePath(dirFilePath: String) {
		infoText.text = String.format(getString(R.string.screen_empty_dir_file_info_text), dirFilePath)
	}
}
