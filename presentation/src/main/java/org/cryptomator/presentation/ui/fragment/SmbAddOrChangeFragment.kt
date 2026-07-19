package org.cryptomator.presentation.ui.fragment

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import org.cryptomator.generator.Fragment
import org.cryptomator.presentation.databinding.FragmentSetupSmbBinding
import org.cryptomator.presentation.model.SmbCloudModel
import org.cryptomator.presentation.presenter.SmbAddOrChangePresenter
import org.cryptomator.util.crypto.CredentialCryptor
import org.cryptomator.util.crypto.FatalCryptoException
import javax.inject.Inject
import timber.log.Timber

@Fragment
class SmbAddOrChangeFragment : BaseFragment<FragmentSetupSmbBinding>(FragmentSetupSmbBinding::inflate) {

	@Inject
	lateinit var smbAddOrChangePresenter: SmbAddOrChangePresenter

	private var cloudId: Long? = null

	private val smbCloudModel: SmbCloudModel?
		get() = arguments?.getSerializable(ARG_SMB_CLOUD) as? SmbCloudModel

	override fun setupView() {
		binding.createCloudButton.setOnClickListener { createCloud() }
		binding.createCloudButton.setOnEditorActionListener { _, actionId, _ ->
			if (actionId == EditorInfo.IME_ACTION_DONE) {
				createCloud()
			}
			false
		}

		binding.urlPortEditText.text?.length?.let { binding.urlPortEditText.setSelection(it) }
		showEditableCloudContent(smbCloudModel)
	}

	private fun showEditableCloudContent(smbCloudModel: SmbCloudModel?) {
		if (smbCloudModel != null) {
			binding.urlPortEditText.setText(smbCloudModel.url())
			binding.userNameEditText.setText(smbCloudModel.username())
			binding.passwordEditText.setText(getPassword(smbCloudModel.password()))
			cloudId = smbCloudModel.id()
		}
	}

	private fun getPassword(password: String?): String {
		return if (password != null) {
			try {
				CredentialCryptor //
					.getInstance(activity?.applicationContext) //
					.decrypt(password)
			} catch (e: FatalCryptoException) {
				Timber.tag("SmbAddOrChangeFragment").e(e, "Unable to decrypt password, clearing it")
				""
			}
		} else ""
	}

	private fun createCloud() {
		val urlPort = binding.urlPortEditText.text.toString().trim()
		val username = binding.userNameEditText.text.toString().trim()
		val password = binding.passwordEditText.text.toString().trim()

		smbAddOrChangePresenter.checkUserInput(urlPort, username, password, cloudId)
	}

	fun hideKeyboard() {
		hideKeyboard(binding.passwordEditText)
	}

	companion object {

		private const val ARG_SMB_CLOUD = "SMB_CLOUD"

		fun newInstance(cloudModel: SmbCloudModel?): SmbAddOrChangeFragment {
			val result = SmbAddOrChangeFragment()
			val args = Bundle()
			args.putSerializable(ARG_SMB_CLOUD, cloudModel)
			result.arguments = args
			return result
		}
	}

}
