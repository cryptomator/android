package org.cryptomator.util

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import androidx.preference.PreferenceManager
import com.google.common.base.Optional
import org.cryptomator.util.LockTimeout.ONE_MINUTE
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.WeakHashMap
import java.util.function.Consumer
import javax.inject.Inject
import kotlin.math.abs

class SharedPreferencesHandler @Inject
constructor(context: Context) : SharedPreferences.OnSharedPreferenceChangeListener {

	private val defaultSharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

	private val lockTimeoutChangedListeners = WeakHashMap<Consumer<LockTimeout>, Void>()

	init {
		defaultSharedPreferences.registerOnSharedPreferenceChangeListener(this)
	}

	val lockTimeout: LockTimeout
		get() {
			val value = defaultSharedPreferences.getValue(LOCK_TIMEOUT, ONE_MINUTE.name)
			return LockTimeout.valueOf(value)
		}

	val screenStyleMode: Int
		get() {
			return when (defaultSharedPreferences.getValue(SCREEN_STYLE_MODE, "MODE_NIGHT_FOLLOW_SYSTEM")) {
				"MODE_NIGHT_FOLLOW_SYSTEM" -> MODE_NIGHT_FOLLOW_SYSTEM
				"MODE_NIGHT_NO" -> MODE_NIGHT_NO
				"MODE_NIGHT_YES" -> MODE_NIGHT_YES
				else -> MODE_NIGHT_FOLLOW_SYSTEM
			}
		}


	fun setScreenStyleMode(newValue: String) {
		defaultSharedPreferences.setValue(SCREEN_STYLE_MODE, newValue)
	}

	val isScreenLockDialogAlreadyShown: Boolean
		get() = defaultSharedPreferences.contains(SCREEN_LOCK_DIALOG_SHOWN)

	fun addLockTimeoutChangedListener(listener: Consumer<LockTimeout>) {
		lockTimeoutChangedListeners[listener] = null
		listener.accept(lockTimeout)
	}

	fun debugMode(): Boolean {
		return defaultSharedPreferences.getValue(DEBUG_MODE, false)
	}

	fun setDebugMode(enabled: Boolean) {
		defaultSharedPreferences //
			.setValue(DEBUG_MODE, enabled)
	}

	fun disableAppWhenObscured(): Boolean {
		return defaultSharedPreferences.getValue(DISABLE_APP_WHEN_OBSCURED, true)
	}

	fun setDisableAppWhenObscured(enabled: Boolean) {
		defaultSharedPreferences //
			.setValue(DISABLE_APP_WHEN_OBSCURED, enabled)
	}

	fun secureScreen(): Boolean {
		return defaultSharedPreferences.getValue(SECURE_SCREEN, true)
	}

	fun setSecureScreen(enabled: Boolean) {
		defaultSharedPreferences //
			.setValue(SECURE_SCREEN, enabled)
	}

	fun lockOnScreenOff(): Boolean {
		return defaultSharedPreferences.getValue(LOCK_ON_SCREEN_OFF, true)
	}

	fun setScreenLockDialogAlreadyShown() {
		defaultSharedPreferences //
			.setValue(SCREEN_LOCK_DIALOG_SHOWN, true)
	}

	fun isBetaModeAlreadyShown(): Boolean {
		return defaultSharedPreferences.getValue(SCREEN_BETA_DIALOG_SHOWN, true)
	}

	fun setBetaScreenDialogAlreadyShown(value: Boolean) {
		defaultSharedPreferences //
			.setValue(SCREEN_BETA_DIALOG_SHOWN, value)
	}

	fun useBiometricAuthentication(): Boolean {
		return defaultSharedPreferences.getValue(USE_BIOMETRIC_AUTHENTICATION, false)
	}

	fun changeUseBiometricAuthentication(useBiometricAuthentication: Boolean) {
		defaultSharedPreferences.setValue(USE_BIOMETRIC_AUTHENTICATION, useBiometricAuthentication)
	}

	fun useConfirmationInFaceUnlockBiometricAuthentication(): Boolean {
		return defaultSharedPreferences.getValue(USE_CONFIRMATION_IN_FACE_UNLOCK_AUTHENTICATION, true)
	}

	fun changeUseConfirmationInFaceUnlockBiometricAuthentication(useConfirmationInFaceUnlockBiometricAuthentication: Boolean) {
		defaultSharedPreferences.setValue(USE_CONFIRMATION_IN_FACE_UNLOCK_AUTHENTICATION, useConfirmationInFaceUnlockBiometricAuthentication)
	}

	fun useLiveSearch(): Boolean {
		return defaultSharedPreferences.getValue(LIVE_SEARCH, false)
	}

	fun useGlobSearch(): Boolean {
		return defaultSharedPreferences.getValue(GLOB_SEARCH, false)
	}

	fun removeAllEntries() {
		defaultSharedPreferences.clear()
	}

	fun usePhotoUpload(): Boolean {
		return defaultSharedPreferences.getValue(PHOTO_UPLOAD, false)
	}

	fun usePhotoUploadInstant(): Boolean {
		return defaultSharedPreferences.getValue(PHOTO_UPLOAD_INSTANT, true)
	}

	fun autoPhotoUploadOnlyUsingWifi(): Boolean {
		return defaultSharedPreferences.getValue(PHOTO_UPLOAD_ONLY_USING_WIFI)
	}

	fun photoUploadVault(): Long {
		return defaultSharedPreferences.getValue(PHOTO_UPLOAD_VAULT, 0)
	}

	fun photoUploadVault(vaultId: Long) {
		defaultSharedPreferences.setValue(PHOTO_UPLOAD_VAULT, vaultId)
	}

	fun photoUploadVaultFolder(): String {
		return defaultSharedPreferences.getValue(PHOTO_UPLOAD_FOLDER, "")
	}

	fun photoUploadVaultFolder(location: String) {
		defaultSharedPreferences.setValue(PHOTO_UPLOAD_FOLDER, location)
	}

	fun autoPhotoUploadIncludingVideos(): Boolean {
		return defaultSharedPreferences.getValue(PHOTO_UPLOAD_INCLUDING_VIDEOS, false)
	}

	fun useLruCache(): Boolean {
		return defaultSharedPreferences.getValue(USE_LRU_CACHE, false)
	}

	override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
		if (LOCK_TIMEOUT == key) {
			val lockTimeout = lockTimeout
			lockTimeoutChangedListeners.keys.forEach { listener ->
				listener.accept(lockTimeout)
			}
		}
	}

	fun lruCacheSize(): Int {
		return defaultSharedPreferences.getValue(LRU_CACHE_SIZE, "100").toInt() * 1024 * 1024
	}

	fun mail(): String {
		return defaultSharedPreferences.getValue(MAIL, "")
	}

	fun setMail(mail: String) {
		defaultSharedPreferences.setValue(MAIL, mail)
	}

	fun keepUnlockedWhileEditing(): Boolean {
		return defaultSharedPreferences.getBoolean(KEEP_UNLOCKED_WHILE_EDITING, false)
	}

	private fun updateIntervalInDays(): Optional<Int> {
		val updateInterval = defaultSharedPreferences.getValue(UPDATE_INTERVAL, "7")

		if (updateInterval == "Never") {
			return Optional.absent()
		}

		return Optional.of(Integer.parseInt(updateInterval))
	}

	fun lastUpdateCheck(): Date? {
		val date = defaultSharedPreferences.getString(LAST_UPDATE_CHECK, "")
		if (date.isNullOrEmpty()) {
			return null
		}
		return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).parse(date)
	}

	fun updateExecuted() {
		val formatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date())
		defaultSharedPreferences.setValue(LAST_UPDATE_CHECK, formatted)
	}

	fun doUpdate(): Boolean {
		val updateIntervalInDays = updateIntervalInDays()
		if (!updateIntervalInDays.isPresent) {
			return false
		}

		val lastUpdateCheck = lastUpdateCheck() ?: return true

		val currentDate = Date()
		val different = abs(currentDate.time - lastUpdateCheck.time)

		val secondsInMilli = 1000
		val minutesInMilli = secondsInMilli * 60
		val hoursInMilli = minutesInMilli * 60
		val daysInMilli = hoursInMilli * 24

		return different / daysInMilli >= updateIntervalInDays.get()
	}

	fun backgroundUnlockPreparation(): Boolean {
		return defaultSharedPreferences.getBoolean(BACKGROUND_UNLOCK_PREPARATION, true)
	}

	fun vaultsRemovedDuringMigration(vaultsToBeRemoved: Pair<String, List<String>>?) {
		vaultsToBeRemoved?.let {
			val vaultsToBeRemovedString = if (it.second.isNotEmpty()) {
				it.second.reduce { acc, s -> "$acc,$s" }
			} else {
				""
			}
			defaultSharedPreferences.setValue(VAULTS_REMOVED_DURING_MIGRATION_TYPE, it.first)
			defaultSharedPreferences.setValue(VAULTS_REMOVED_DURING_MIGRATION, vaultsToBeRemovedString)
		} ?: run {
			defaultSharedPreferences.setValue(VAULTS_REMOVED_DURING_MIGRATION_TYPE, null)
			defaultSharedPreferences.setValue(VAULTS_REMOVED_DURING_MIGRATION, null)
		}
	}

	fun vaultsRemovedDuringMigration(): Pair<String, List<String>>? {
		val vaultsRemovedDuringMigrationType = defaultSharedPreferences.getString(VAULTS_REMOVED_DURING_MIGRATION_TYPE, null)
		val vaultsRemovedDuringMigration = defaultSharedPreferences.getString(VAULTS_REMOVED_DURING_MIGRATION, null)
		return if(vaultsRemovedDuringMigrationType != null && vaultsRemovedDuringMigration != null) {
			Pair(vaultsRemovedDuringMigrationType, ArrayList(vaultsRemovedDuringMigration.split(',')))
		} else {
			null
		}
	}

	companion object {

		private const val SCREEN_LOCK_DIALOG_SHOWN = "askForScreenLockDialogShown"
		private const val SCREEN_BETA_DIALOG_SHOWN = "askForBetaConfirmationDialogShown"
		private const val USE_BIOMETRIC_AUTHENTICATION = "useFingerprint"
		private const val USE_CONFIRMATION_IN_FACE_UNLOCK_AUTHENTICATION = "useConfirmationInFaceUnlockBiometricAuthentication"
		private const val LOCK_TIMEOUT = "lockTimeout"
		private const val LOCK_ON_SCREEN_OFF = "lockOnScreenOff"
		private const val LIVE_SEARCH = "liveSearch"
		private const val GLOB_SEARCH = "globSearch"
		private const val KEEP_UNLOCKED_WHILE_EDITING = "keepUnlockedWhileEditing"
		private const val BACKGROUND_UNLOCK_PREPARATION = "backgroundUnlockPreparation"
		private const val VAULTS_REMOVED_DURING_MIGRATION = "vaultsRemovedDuringMigration"
		private const val VAULTS_REMOVED_DURING_MIGRATION_TYPE = "vaultsRemovedDuringMigrationType"
		const val DEBUG_MODE = "debugMode"
		const val DISABLE_APP_WHEN_OBSCURED = "disableAppWhenObscured"
		const val SECURE_SCREEN = "secureScreen"
		const val SCREEN_STYLE_MODE = "screenStyleMode"
		const val PHOTO_UPLOAD = "photoUpload"
		const val PHOTO_UPLOAD_INSTANT = "photoUploadInstant"
		const val PHOTO_UPLOAD_ONLY_USING_WIFI = "photoUploadOnlyUsingWifi"
		const val PHOTO_UPLOAD_VAULT = "photoUploadVault"
		const val PHOTO_UPLOAD_FOLDER = "photoUploadFolder"
		const val PHOTO_UPLOAD_INCLUDING_VIDEOS = "photoUploadIncludingVideos"
		const val USE_LRU_CACHE = "lruCache"
		const val LRU_CACHE_SIZE = "lruCacheSize"
		const val MAIL = "mail"
		const val UPDATE_INTERVAL = "updateInterval"
		private const val LAST_UPDATE_CHECK = "lastUpdateCheck"
	}

	private inline fun SharedPreferences.edit(operation: (SharedPreferences.Editor) -> Unit) {
		val editor = this.edit()
		operation(editor)
		editor.apply()
	}

	private fun SharedPreferences.clear() {
		val editor = this.edit()
		editor.clear()
		editor.apply()
	}

	private fun SharedPreferences.setValue(key: String, value: Any?) {
		set(key, value)
	}

	private operator fun SharedPreferences.set(key: String, value: Any?) {
		when (value) {
			is String? -> edit { it.putString(key, value) }
			is Int -> edit { it.putInt(key, value) }
			is Boolean -> edit { it.putBoolean(key, value) }
			is Float -> edit { it.putFloat(key, value) }
			is Long -> edit { it.putLong(key, value) }
			else -> throw UnsupportedOperationException("Not yet implemented")
		}
	}

	/**
	 * finds value on given key.
	 * [T] is the type of value
	 * @param defaultValue optional default value - will take null for strings, false for bool and -1 for numeric values if [defaultValue] is not specified
	 */
	private inline fun <reified T : Any> SharedPreferences.getValue(key: String, defaultValue: T? = null): T {
		return get(key, defaultValue)
	}

	/**
	 * finds value on given key.
	 * [T] is the type of value
	 * @param defaultValue optional default value - will take null for strings, false for bool and -1 for numeric values if [defaultValue] is not specified
	 */
	private inline operator fun <reified T : Any> SharedPreferences.get(key: String, defaultValue: T? = null): T {
		return when (T::class) {
			String::class -> getString(key, defaultValue as? String) as T
			Int::class -> getInt(key, defaultValue as? Int ?: -1) as T
			Boolean::class -> getBoolean(key, defaultValue as? Boolean ?: false) as T
			Float::class -> getFloat(key, defaultValue as? Float ?: -1f) as T
			Long::class -> getLong(key, defaultValue as? Long ?: -1) as T
			else -> throw UnsupportedOperationException("Not yet implemented")
		}
	}
}
