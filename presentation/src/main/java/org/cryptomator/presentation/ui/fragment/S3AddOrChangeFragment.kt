package org.cryptomator.presentation.ui.fragment

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import org.cryptomator.generator.Fragment
import org.cryptomator.presentation.databinding.FragmentSetupS3Binding
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

	private val s3CloudModel: S3CloudModel?
		get() = arguments?.getSerializable(ARG_S3_CLOUD) as? S3CloudModel

	override fun setupView() {
		binding.createCloudButton.setOnClickListener { createCloud() }
		binding.createCloudButton.setOnEditorActionListener { _, actionId, _ ->
			if (actionId == EditorInfo.IME_ACTION_DONE) {
				createCloud()
			}
			false
		}

		showEditableCloudContent(s3CloudModel)
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
		val displayName = binding.displayNameEditText.text.toString().trim()

		s3AddOrChangePresenter.checkUserInput( //
			accessKey, //
			secretKey, //
			bucket, //
			binding.endpointEditText.text.toString().trim(), //
			binding.regionEditText.text.toString().trim(), //
			cloudId, //
			displayName
		)
	}

	fun hideKeyboard() {
		hideKeyboard(binding.bucketEditText)
	}

	companion object {

		private const val ARG_S3_CLOUD = "S3_CLOUD"

		fun newInstance(cloudModel: S3CloudModel?): S3AddOrChangeFragment {
			val result = S3AddOrChangeFragment()
			val args = Bundle()
			args.putSerializable(ARG_S3_CLOUD, cloudModel)
			result.arguments = args
			return result
		}
	}

}
