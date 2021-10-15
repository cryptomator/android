package org.cryptomator.presentation.ui.callback

import org.cryptomator.presentation.ui.bottomsheet.AddVaultBottomSheet
import org.cryptomator.presentation.ui.bottomsheet.SettingsVaultBottomSheet
import org.cryptomator.presentation.ui.dialog.VaultDeleteConfirmationDialog
import org.cryptomator.presentation.ui.dialog.VaultRenameDialog

// FIXME delete this file and add this interfaces to VaultListView.kt
interface VaultListCallback : AddVaultBottomSheet.Callback, //
	SettingsVaultBottomSheet.Callback, //
	VaultDeleteConfirmationDialog.Callback, //
	VaultRenameDialog.Callback
