package org.cryptomator.presentation.ui.activity.view

interface TextEditorView : View {

	val textFileContent: String

	fun performBackPressed()
	fun showUnsavedChangesDialog()
	fun displayTextFileContent(textFileContent: String)

}
