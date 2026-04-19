package org.cryptomator.presentation.licensing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.annotation.StringRes
import org.cryptomator.domain.di.PerView
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

@PerView
class LicenseEnforcer @Inject constructor(private val sharedPreferencesHandler: SharedPreferencesHandler) {

	enum class LockedAction(
		@StringRes val toastMessageRes: Int,
		@StringRes val headerMessageRes: Int
	) {
		CREATE_VAULT(
			R.string.read_only_reason_create_vault,
			R.string.screen_license_check_locked_create_vault,
		),
		UPLOAD_FILES(
			R.string.read_only_reason_add_file,
			R.string.screen_license_check_locked_upload_files,
		),
		CREATE_FOLDER(
			R.string.read_only_reason_create_folder,
			R.string.screen_license_check_locked_create_folder,
		),
		CREATE_TEXT_FILE(
			R.string.read_only_reason_create_text_file,
			R.string.screen_license_check_locked_create_text_file,
		),
		SHARE_NODE(
			R.string.read_only_reason_share_node,
			R.string.screen_license_check_locked_share_node,
		),
		RENAME_NODE(
			R.string.read_only_reason_rename_node,
			R.string.screen_license_check_locked_rename_node,
		),
		MOVE_NODE(
			R.string.read_only_reason_move_node,
			R.string.screen_license_check_locked_move_node,
		),
		DELETE_NODE(
			R.string.read_only_reason_delete_node,
			R.string.screen_license_check_locked_delete_node,
		);

		companion object {
			/**
			 * Finds the LockedAction whose enum name matches the provided string.
			 *
			 * @param name The enum name to match (case-sensitive); may be `null`.
			 * @return The matching `LockedAction`, or `null` if no match is found.
			 */
			fun fromName(name: String?): LockedAction? {
				return values().firstOrNull { it.name == name }
			}
		}
	}

	/**
	 * Determines whether write operations are permitted for the current user.
	 *
	 * @return `true` if a paid license is present or an active trial exists, `false` otherwise.
	 */
	fun hasWriteAccess(): Boolean {
		return hasPaidLicense() || hasActiveTrial()
	}

	/**
	 * Determines whether the user should be treated as having a paid license.
	 *
	 * @return `true` if a paid license is present (premium flavor, a stored license token, or a running subscription), `false` otherwise.
	 */
	fun hasPaidLicense(): Boolean {
		if (FlavorConfig.isPremiumFlavor) {
			return true
		}
		if (sharedPreferencesHandler.licenseToken().isNotEmpty()) {
			return true
		}
		if (sharedPreferencesHandler.hasRunningSubscription()) {
			return true
		}
		return false
	}

	/**
	 * Starts a 30-day trial by setting the trial expiration timestamp in preferences if none is set.
	 *
	 * If a trial expiration is already present (> 0), the method returns without changing it. Otherwise
	 * it stores System.currentTimeMillis() + 30 days as the trial expiration date.
	 */
	fun startTrial() {
		if (sharedPreferencesHandler.trialExpirationDate() > 0) {
			return
		}
		val trialExpiration = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(30)
		sharedPreferencesHandler.setTrialExpirationDate(trialExpiration)
	}

	/**
	 * Checks whether the stored trial expiration timestamp is due and, if so and it is not already marked,
	 * sets the trial expired flag in preferences.
	 */
	private fun observeTrialExpiry() {
		val trialExpiration = sharedPreferencesHandler.trialExpirationDate()
		val now = System.currentTimeMillis()
		if (trialExpiration > 0 && trialExpiration <= now && !sharedPreferencesHandler.isTrialExpired()) {
			sharedPreferencesHandler.setTrialExpired(true)
		}
	}

	/**
	 * Determine whether a trial period is currently active.
	 *
	 * Observes expiry state before evaluation to ensure sticky/expired flags are up to date.
	 *
	 * @return `true` if a trial expiration date is set, is in the future, and the trial is not marked expired; `false` otherwise.
	 */
	fun hasActiveTrial(): Boolean {
		observeTrialExpiry()
		val trialExpiration = sharedPreferencesHandler.trialExpirationDate()
		return trialExpiration > 0 && trialExpiration > System.currentTimeMillis() && !sharedPreferencesHandler.isTrialExpired()
	}

	/**
	 * Computes the current trial status and a human-readable expiration date when applicable.
	 *
	 * Evaluates whether a trial is active, expired, and returns a formatted expiration date if an expiration is set and either active or expired.
	 *
	 * @return A [TrialState] containing:
	 *  - `isActive`: `true` when a future expiration is set and the trial is not marked expired.
	 *  - `isExpired`: `true` when an expiration is set and has passed or the trial is marked expired.
	 *  - `formattedExpirationDate`: a locale-formatted date string when an expiration is present and the trial is active or expired, or `null` otherwise.
	 */
	fun evaluateTrialState(): TrialState {
		observeTrialExpiry()
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
		val trialState: TrialState,
		val trialExpirationText: String?
	)

	/**
	 * Builds the license-related UI state used by screens to reflect access and trial status.
	 *
	 * @param context Context used to resolve the localized trial expiration string when applicable.
	 * @return A LicenseUiState containing write-access and paid-license flags, the evaluated TrialState, and an optional localized trialExpirationText (present when the trial is active or expired).
	 */
	fun evaluateUiState(context: Context): LicenseUiState {
		val trialState = evaluateTrialState()
		val expirationText = if (trialState.isActive || trialState.isExpired) {
			context.getString(R.string.screen_license_check_trial_expiration, trialState.formattedExpirationDate)
		} else null
		return LicenseUiState(
			hasWriteAccess = hasWriteAccess(),
			hasPaidLicense = hasPaidLicense(),
			trialState = trialState,
			trialExpirationText = expirationText
		)
	}

	/**
	 * Provides the default string resource used as the reason banner when write access is restricted.
	 *
	 * @return The string resource id for the default read-only banner.
	 */
	@StringRes
	fun defaultReasonRes(): Int = R.string.read_only_banner

	/**
	 * Checks whether the requested write action is permitted and, if not, notifies the user and (for non-premium builds) opens the license-check screen.
	 *
	 * @param activity Activity used to show UI and to launch the license-check flow.
	 * @param action The locked action being attempted; used to select the user-facing message and to indicate which action is locked when launching the license-check screen.
	 * @return `true` if write access is allowed for the requested action, `false` otherwise.
	 */
	fun ensureWriteAccess(activity: Activity, action: LockedAction): Boolean {
		if (hasWriteAccess()) {
			return true
		}

		Toast.makeText(activity, activity.getString(action.toastMessageRes), Toast.LENGTH_LONG).show()

		if (FlavorConfig.isPremiumFlavor) {
			return false
		}

		val intent = Intents.licenseCheckIntent()
			.withLockedAction(action.name)
			.build(activity as ContextHolder)
		intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
		activity.startActivity(intent)
		return false
	}

	/**
	 * Determine whether write actions are permitted for the given vault.
	 *
	 * For hub vaults, returns `true` if the vault has a hub-paid license or app-wide write access is available.
	 * For non-hub or `null` vaults, returns whether app-wide write access is available.
	 *
	 * @param vault The vault to check; may be `null` (treated as non-hub).
	 * @return `true` if write actions are allowed for the provided vault, `false` otherwise.
	 */
	fun hasWriteAccessForVault(vault: VaultModel?): Boolean {
		if (vault?.isHubVault == true) {
			return vault.hasHubPaidLicense || hasWriteAccess()
		}
		return hasWriteAccess()
	}

	/**
	 * Checks and enforces write access for the specified vault, treating hub vaults with their own restrictions.
	 *
	 * @param activity Activity used to display UI and to launch the license-check flow when enforcement requires user interaction.
	 * @param vault The vault to validate; if `null` or not a hub vault, global write-access enforcement is applied.
	 * @param action The write action being attempted, used to determine the appropriate enforcement messaging or flow.
	 * @return `true` if write access is granted for the requested action, `false` otherwise.
	 */
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
