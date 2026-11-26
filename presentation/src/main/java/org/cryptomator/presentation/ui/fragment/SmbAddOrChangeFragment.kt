package org.cryptomator.presentation.ui.fragment

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.core.os.BundleCompat
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
		get() = arguments?.let { BundleCompat.getSerializable(it, ARG_SMB_CLOUD, SmbCloudModel::class.java) }

	override fun setupView() {
		binding.createCloudButton.setOnClickListener { createCloud() }
		binding.passwordEditText.setOnEditorActionListener { _, actionId, _ ->
			if (actionId == EditorInfo.IME_ACTION_DONE) {
				createCloud()
			}
			false
		}

		showEditableCloudContent(smbCloudModel)
		binding.urlEditText.text?.length?.let { binding.urlEditText.setSelection(it) }
	}

	private fun showEditableCloudContent(smbCloudModel: SmbCloudModel?) {
		if (smbCloudModel != null) {
			binding.urlEditText.setText(smbCloudModel.url())
			binding.userNameEditText.setText(smbCloudModel.username())
			binding.passwordEditText.setText(getPassword(smbCloudModel.password()))
			cloudId = smbCloudModel.id()
		}
	}

	private fun getPassword(password: String): String {
		return try {
			CredentialCryptor //
				.getInstance(activity?.applicationContext) //
				.decrypt(password)
		} catch (e: FatalCryptoException) {
			Timber.tag("SmbAddOrChangeFragment").e(e, "Unable to decrypt password, clearing it")
			""
		}
	}

	private fun createCloud() {
		val url = binding.urlEditText.text.toString()
		val username = binding.userNameEditText.text.toString().trim()
		val password = binding.passwordEditText.text.toString()

		smbAddOrChangePresenter.checkUserInput(url, username, password, cloudId)
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
