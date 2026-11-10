package org.cryptomator.presentation.ui.fragment

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import org.cryptomator.domain.WebDavProvider
import org.cryptomator.generator.Fragment
import org.cryptomator.presentation.databinding.FragmentSetupWebdavBinding
import org.cryptomator.presentation.model.CloudTypeModel
import org.cryptomator.presentation.model.WebDavCloudModel
import org.cryptomator.presentation.presenter.WebDavAddOrChangePresenter
import org.cryptomator.util.crypto.CredentialCryptor
import org.cryptomator.util.crypto.FatalCryptoException
import javax.inject.Inject
import timber.log.Timber

@Fragment
class WebDavAddOrChangeFragment : BaseFragment<FragmentSetupWebdavBinding>(FragmentSetupWebdavBinding::inflate) {

	@Inject
	lateinit var webDavAddOrChangePresenter: WebDavAddOrChangePresenter

	private var cloudId: Long? = null
	private var certificate: String? = null

	private val webDavCloudModel: WebDavCloudModel?
		get() = arguments?.getSerializable(ARG_WEBDAV_CLOUD) as? WebDavCloudModel

	private val webDavProvider: WebDavProvider?
		get() = arguments?.getSerializable(ARG_WEBDAV_PROVIDER) as? WebDavProvider

	private val isNewProviderCloud: Boolean
		get() = webDavProvider != null && webDavCloudModel == null

	override fun setupView() {
		binding.createCloudButton.setOnClickListener { createCloud() }
		binding.createCloudButton.setOnEditorActionListener { _, actionId, _ ->
			if (actionId == EditorInfo.IME_ACTION_DONE) {
				createCloud()
			}
			false
		}

		binding.urlPortEditText.text?.length?.let { binding.urlPortEditText.setSelection(it) }
		showEditableCloudContent(webDavCloudModel)
		webDavProvider?.let { showProviderContent(it) }
	}

	private fun showProviderContent(webDavProvider: WebDavProvider) {
		val hintResource = CloudTypeModel.valueOf(webDavProvider).webDavHintResource
		if (hintResource != 0) {
			binding.hintText.setText(hintResource)
			binding.hintText.visibility = View.VISIBLE
		}
		if (isNewProviderCloud) {
			binding.urlPortLayout.visibility = View.GONE
			binding.serverLayout.visibility = if (webDavProvider.requiresServer) View.VISIBLE else View.GONE
			binding.spaceIdLayout.visibility = if (webDavProvider.requiresSpaceId) View.VISIBLE else View.GONE
		}
	}

	private fun showEditableCloudContent(webDavCloudModel: WebDavCloudModel?) {
		if (webDavCloudModel != null) {
			binding.urlPortEditText.setText(webDavCloudModel.url())
			binding.userNameEditText.setText(webDavCloudModel.username())
			binding.passwordEditText.setText(getPassword(webDavCloudModel.accessToken()))
			cloudId = webDavCloudModel.id()
			certificate = webDavCloudModel.certificate()
		}
	}

	private fun getPassword(password: String?): String {
		return if (password != null) {
			try {
				CredentialCryptor //
					.getInstance(activity?.applicationContext) //
					.decrypt(password)
			} catch (e: FatalCryptoException) {
				Timber.tag("WebdavAddOrCangeFragmnt").e(e, "Unable to decrypt password, clearing it")
				""
			}
		} else ""
	}

	private fun createCloud() {
		val urlPort = binding.urlPortEditText.text.toString().trim()
		val server = binding.serverEditText.text.toString().trim()
		val spaceId = binding.spaceIdEditText.text.toString().trim()
		val username = binding.userNameEditText.text.toString().trim()
		val password = binding.passwordEditText.text.toString().trim()

		if (isNewProviderCloud) {
			webDavAddOrChangePresenter.checkProviderInput(server, spaceId, username, password)
		} else {
			webDavAddOrChangePresenter.checkUserInput(urlPort, username, password, cloudId, certificate)
		}
	}

	fun hideKeyboard() {
		hideKeyboard(binding.passwordEditText)
	}

	companion object {

		private const val ARG_WEBDAV_CLOUD = "WEBDAV_CLOUD"
		private const val ARG_WEBDAV_PROVIDER = "WEBDAV_PROVIDER"

		fun newInstance(cloudModel: WebDavCloudModel?, webDavProvider: WebDavProvider?): WebDavAddOrChangeFragment {
			val result = WebDavAddOrChangeFragment()
			val args = Bundle()
			args.putSerializable(ARG_WEBDAV_CLOUD, cloudModel)
			args.putSerializable(ARG_WEBDAV_PROVIDER, webDavProvider)
			result.arguments = args
			return result
		}
	}

}
