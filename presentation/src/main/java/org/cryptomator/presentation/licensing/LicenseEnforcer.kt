package org.cryptomator.presentation.licensing

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.annotation.StringRes
import org.cryptomator.presentation.R
import org.cryptomator.presentation.intent.Intents
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.presentation.presenter.ContextHolder
import org.cryptomator.util.FlavorConfig
import org.cryptomator.util.SharedPreferencesHandler
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class LicenseEnforcer @Inject constructor(private val sharedPreferencesHandler: SharedPreferencesHandler) {

	enum class LockedAction(
		@StringRes val headerMessageRes: Int
	) {
		CREATE_VAULT(R.string.screen_license_check_locked_create_vault),
		UPLOAD_FILES(R.string.screen_license_check_locked_upload_files),
		CREATE_FOLDER(R.string.screen_license_check_locked_create_folder),
		CREATE_TEXT_FILE(R.string.screen_license_check_locked_create_text_file),
		SHARE_NODE(R.string.screen_license_check_locked_share_node),
		RENAME_NODE(R.string.screen_license_check_locked_rename_node),
		MOVE_NODE(R.string.screen_license_check_locked_move_node),
		DELETE_NODE(R.string.screen_license_check_locked_delete_node);

		companion object {
			fun fromName(name: String?): LockedAction? {
				return entries.firstOrNull { it.name == name }
			}
		}
	}

	fun hasWriteAccess(): Boolean {
		return hasPaidLicense() || hasActiveTrial()
	}

	fun hasPaidLicense() =
		FlavorConfig.isPremiumFlavor ||
			sharedPreferencesHandler.licenseToken().isNotEmpty() ||
			sharedPreferencesHandler.hasRunningSubscription()

	fun startTrial() {
		if (sharedPreferencesHandler.trialExpirationDate() > 0) {
			return
		}
		val trialExpiration = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(30)
		sharedPreferencesHandler.setTrialExpirationDate(trialExpiration)
	}

	fun hasActiveTrial(): Boolean = evaluateTrialState().isActive

	fun evaluateTrialState(): TrialState {
		val state = readTrialState()
		if (state.isExpired && !sharedPreferencesHandler.isTrialExpired()) {
			sharedPreferencesHandler.setTrialExpired(true)
		}
		return state
	}

	private fun readTrialState(): TrialState {
		val trialExpiration = sharedPreferencesHandler.trialExpirationDate()
		val now = System.currentTimeMillis()
		val sticky = sharedPreferencesHandler.isTrialExpired()
		val active = trialExpiration > 0 && trialExpiration > now && !sticky
		val expired = trialExpiration > 0 && (trialExpiration <= now || sticky)
		val formattedDate = if (active || expired) {
			DateFormat.getDateInstance().format(Date(trialExpiration))
		} else null
		return TrialState(active, expired, formattedDate)
	}

	data class TrialState(val isActive: Boolean, val isExpired: Boolean, val formattedExpirationDate: String?)

	data class LicenseUiState(
		val hasWriteAccess: Boolean,
		val hasPaidLicense: Boolean,
		val hasLifetimeLicense: Boolean,
		val hasRunningSubscription: Boolean,
		val trialState: TrialState
	)

	fun evaluateUiState(): LicenseUiState {
		val trialState = evaluateTrialState()
		val paidLicense = hasPaidLicense()
		return LicenseUiState(
			hasWriteAccess = paidLicense || trialState.isActive,
			hasPaidLicense = paidLicense,
			hasLifetimeLicense = sharedPreferencesHandler.licenseToken().isNotEmpty(),
			hasRunningSubscription = sharedPreferencesHandler.hasRunningSubscription(),
			trialState = trialState
		)
	}

	fun ensureWriteAccess(activity: Activity, action: LockedAction): Boolean {
		if (hasWriteAccess()) {
			return true
		}

		val intent = Intents.licenseCheckIntent()
			.withLockedAction(action.name)
			.build(activity as ContextHolder)
		intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
		activity.startActivity(intent)
		return false
	}

	fun hasWriteAccessForVault(vault: VaultModel?): Boolean {
		if (vault?.isHubVault == true) {
			return vault.hasHubPaidLicense || hasWriteAccess()
		}
		return hasWriteAccess()
	}

	fun ensureWriteAccessForVault(activity: Activity, vault: VaultModel?, action: LockedAction): Boolean {
		if (vault?.isHubVault == true) {
			if (hasWriteAccessForVault(vault)) {
				return true
			}
			Toast.makeText(activity, R.string.read_only_reason_hub_inactive, Toast.LENGTH_LONG).show()
			return false
		}
		return ensureWriteAccess(activity, action)
	}

}
