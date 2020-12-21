package org.cryptomator.presentation.ui.callback

import org.cryptomator.presentation.ui.bottomsheet.AddVaultBottomSheet
import org.cryptomator.presentation.ui.bottomsheet.SettingsVaultBottomSheet
import org.cryptomator.presentation.ui.dialog.EnterPasswordDialog
import org.cryptomator.presentation.ui.dialog.VaultDeleteConfirmationDialog
import org.cryptomator.presentation.ui.dialog.VaultRenameDialog

interface VaultListCallback : AddVaultBottomSheet.Callback, EnterPasswordDialog.Callback, SettingsVaultBottomSheet.Callback, VaultDeleteConfirmationDialog.Callback, VaultRenameDialog.Callback
