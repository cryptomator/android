package org.cryptomator.presentation.model

import org.cryptomator.presentation.R
import java.io.Serializable

open class ProgressStateModel private constructor(private val name: String, image: Image, text: Text, selectable: Boolean) : Serializable {

	private val imageResourceId: Int = image.id()
	private val textResourceId: Int = text.id()
	val isSelectable: Boolean = selectable

	private constructor(name: String) : this(name, noImage(), noText())
	private constructor(name: String, text: Text) : this(name, noImage(), text)
	private constructor(name: String, text: Text, selectable: Boolean) : this(name, noImage(), text, selectable)
	internal constructor(name: String, image: Image, text: Text) : this(name, image, text, true)

	fun imageResourceId(): Int {
		return imageResourceId
	}

	fun textResourceId(): Int {
		return textResourceId
	}

	interface Image {

		fun id(): Int
	}

	interface Text {

		fun id(): Int
	}

	fun name(): String {
		return name
	}

	companion object {

		val AUTHENTICATION = ProgressStateModel("AUTHENTICATION", text(R.string.action_progress_authentication))
		val RENAMING = ProgressStateModel("RENAMING", text(R.string.action_progress_renaming))
		val MOVING = ProgressStateModel("MOVING", text(R.string.action_progress_moving), false)
		val DELETION = ProgressStateModel("DELETION", text(R.string.action_progress_deleting), false)
		val CREATING_FOLDER = ProgressStateModel("FOLDER", text(R.string.dialog_progress_creating_folder))
		val CREATING_TEXT_FILE = ProgressStateModel("FILE", text(R.string.dialog_progress_creating_text_file))
		val UNLOCKING_VAULT = ProgressStateModel("VAULT", text(R.string.dialog_progress_unlocking_vault))
		val CHANGING_PASSWORD = ProgressStateModel("PASSWORD", text(R.string.dialog_progress_change_password))
		val CREATING_VAULT = ProgressStateModel("VAULT", text(R.string.dialog_progress_creating_vault))
		val CREATING_HUB_DEVICE = ProgressStateModel("HUB_DEVICE", text(R.string.dialog_progress_creating_hub_device_setup))
		val UNKNOWN = ProgressStateModel("UNKNOWN_MIMETYPE", text(R.string.dialog_progress_please_wait))
		val COMPLETED = ProgressStateModel("COMPLETED")

		// utils
		fun noImage(): Image {
			return image(0)
		}

		fun noText(): Text {
			return text(0)
		}

		fun image(id: Int): Image {
			return object : Image {
				override fun id(): Int {
					return id
				}
			}
		}

		fun text(id: Int): Text {
			return object : Text {
				override fun id(): Int {
					return id
				}
			}
		}
	}

}
