package org.cryptomator.presentation.ui.dialog

import android.app.Activity
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import kotlinx.android.synthetic.main.dialog_vaults_removed_during_migration.tv_message

@Dialog(R.layout.dialog_vaults_removed_during_migration)
class VaultsRemovedDuringMigrationDialog : BaseDialog<Activity>() {

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		val vaultsRemovedDuringMigration = requireArguments().getSerializable(VAULTS_REMOVED_ARG) as Pair<String, ArrayList<String>>

		return builder //
			.setTitle(String.format(getString(R.string.dialog_vaults_removed_during_migration_title), vaultsRemovedDuringMigration.first)) //
			.setNeutralButton(R.string.dialog_vaults_removed_during_migration_neutral_button) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
			.create()
	}

	public override fun setupView() {
		val vaultsRemovedDuringMigration = requireArguments().getSerializable(VAULTS_REMOVED_ARG) as Pair<String, ArrayList<String>>

		val vaultsRemovedDuringMigrationString = vaultsRemovedDuringMigration
			.second
			.map { path -> "* $path" }
			.reduce { acc, s -> "$acc\n$s" }

		tv_message.text = String.format(getString(R.string.dialog_vaults_removed_during_migration_hint), vaultsRemovedDuringMigrationString)
	}

	companion object {

		private const val VAULTS_REMOVED_ARG = "vaultsRemovedArg"

		fun newInstance(vaultsRemovedDuringMigration: Pair<String, List<String>>): DialogFragment {
			val args = Bundle()
			args.putSerializable(VAULTS_REMOVED_ARG, vaultsRemovedDuringMigration)
			val fragment = VaultsRemovedDuringMigrationDialog()
			fragment.arguments = args
			return fragment
		}
	}
}
