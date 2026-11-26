package org.cryptomator.presentation.ui.fragment

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import org.cryptomator.domain.S3Provider
import org.cryptomator.generator.Fragment
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.FragmentSetupS3Binding
import org.cryptomator.presentation.model.CloudTypeModel
import org.cryptomator.presentation.model.S3CloudModel
import org.cryptomator.presentation.presenter.S3AddOrChangePresenter
import org.cryptomator.util.crypto.CredentialCryptor
import org.cryptomator.util.crypto.FatalCryptoException
import javax.inject.Inject
import timber.log.Timber

@Fragment
class S3AddOrChangeFragment : BaseFragment<FragmentSetupS3Binding>(FragmentSetupS3Binding::inflate) {

	@Inject
	lateinit var s3AddOrChangePresenter: S3AddOrChangePresenter

	private var cloudId: Long? = null
	private var selectedChoice: Int? = null

	private val s3CloudModel: S3CloudModel?
		get() = arguments?.getSerializable(ARG_S3_CLOUD) as? S3CloudModel

	private val s3Provider: S3Provider?
		get() = arguments?.getSerializable(ARG_S3_PROVIDER) as? S3Provider

	private val isNewProviderCloud: Boolean
		get() = s3Provider != null && s3CloudModel == null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		selectedChoice = savedInstanceState?.getSerializable(STATE_SELECTED_CHOICE) as? Int
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		outState.putSerializable(STATE_SELECTED_CHOICE, selectedChoice)
	}

	override fun setupView() {
		binding.createCloudButton.setOnClickListener { createCloud() }
		binding.createCloudButton.setOnEditorActionListener { _, actionId, _ ->
			if (actionId == EditorInfo.IME_ACTION_DONE) {
				createCloud()
			}
			false
		}

		showEditableCloudContent(s3CloudModel)
		s3Provider?.let { showProviderContent(it) }
	}

	private fun showProviderContent(s3Provider: S3Provider) {
		val hintResource = CloudTypeModel.valueOf(s3Provider).providerHintResource
		if (hintResource != 0) {
			binding.hintText.setText(hintResource)
			binding.hintText.visibility = View.VISIBLE
		}
		if (isNewProviderCloud) {
			binding.endpointLayout.visibility = View.GONE
			binding.serverLayout.visibility = if (s3Provider.requiresServer) View.VISIBLE else View.GONE
			binding.accountIdLayout.visibility = if (s3Provider.requiresAccountId) View.VISIBLE else View.GONE
			binding.regionLayout.visibility = View.GONE
			showChoices(s3Provider)
		}
	}

	private fun showChoices(s3Provider: S3Provider) {
		if (s3Provider.choices.isEmpty()) {
			return
		}
		val items = s3Provider.choices + getString(R.string.screen_s3_settings_other_choice)
		val label = if (s3Provider.choiceIsEndpointName) R.string.screen_s3_settings_endpoint_name_label else R.string.screen_s3_settings_region_label
		binding.choiceLayout.setHint(label)
		binding.choiceLayout.visibility = View.VISIBLE
		binding.regionLayout.setHint(label)
		binding.regionLayout.helperText = getString(R.string.screen_s3_settings_example, s3Provider.choices.first())
		binding.choiceDropdown.setSimpleItems(items.toTypedArray())
		binding.choiceDropdown.setOnItemClickListener { _, _, position, _ -> showSelectedChoice(s3Provider, position) }
		selectedChoice?.let {
			binding.choiceDropdown.setText(items[it], false)
			showSelectedChoice(s3Provider, it)
		}
	}

	private fun showSelectedChoice(s3Provider: S3Provider, position: Int) {
		selectedChoice = position
		binding.regionLayout.visibility = if (position == s3Provider.choices.size) View.VISIBLE else View.GONE
	}

	private fun showEditableCloudContent(s3CloudModel: S3CloudModel?) {
		s3CloudModel?.let {
			cloudId = s3CloudModel.id()
			binding.displayNameEditText.setText(s3CloudModel.username())
			binding.accessKeyEditText.setText(decrypt(s3CloudModel.accessKey()))
			binding.secretKeyEditText.setText(decrypt(s3CloudModel.secretKey()))
			binding.bucketEditText.setText(s3CloudModel.s3Bucket())
			binding.endpointEditText.setText(s3CloudModel.s3Endpoint())
			binding.regionEditText.setText(s3CloudModel.s3Region())
		}
	}

	private fun decrypt(text: String?): String {
		return if (text != null) {
			try {
				CredentialCryptor //
					.getInstance(activity?.applicationContext) //
					.decrypt(text)
			} catch (e: FatalCryptoException) {
				Timber.tag("S3AddOrChangeFragment").e(e, "Unable to decrypt password, clearing it")
				""
			}
		} else ""
	}

	private fun createCloud() {
		val accessKey = binding.accessKeyEditText.text.toString().trim()
		val secretKey = binding.secretKeyEditText.text.toString().trim()
		val bucket = binding.bucketEditText.text.toString().trim()
		val endpoint = binding.endpointEditText.text.toString().trim()
		val server = binding.serverEditText.text.toString().trim()
		val accountId = binding.accountIdEditText.text.toString().trim()
		val region = binding.regionEditText.text.toString().trim()
		val displayName = binding.displayNameEditText.text.toString().trim()

		if (isNewProviderCloud) {
			s3AddOrChangePresenter.checkProviderInput(accessKey, secretKey, bucket, server, accountId, selectedChoice, region, displayName)
		} else {
			s3AddOrChangePresenter.checkUserInput(accessKey, secretKey, bucket, endpoint, region, cloudId, displayName)
		}
	}

	fun hideKeyboard() {
		hideKeyboard(binding.bucketEditText)
	}

	companion object {

		private const val ARG_S3_CLOUD = "S3_CLOUD"
		private const val ARG_S3_PROVIDER = "S3_PROVIDER"
		private const val STATE_SELECTED_CHOICE = "SELECTED_CHOICE"

		fun newInstance(cloudModel: S3CloudModel?, s3Provider: S3Provider?): S3AddOrChangeFragment {
			val result = S3AddOrChangeFragment()
			val args = Bundle()
			args.putSerializable(ARG_S3_CLOUD, cloudModel)
			args.putSerializable(ARG_S3_PROVIDER, s3Provider)
			result.arguments = args
			return result
		}
	}

}
