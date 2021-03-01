package org.cryptomator.presentation.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.util.ArrayList

class EmailBuilder private constructor() {

	private var recipient: String? = null
	private var subject: String? = null
	private var body: String? = null
	private val attachments: MutableList<File> = ArrayList()
	fun to(recipient: String?): EmailBuilder {
		this.recipient = recipient
		return this
	}

	fun withSubject(subject: String?): EmailBuilder {
		this.subject = subject
		return this
	}

	fun withBody(body: String?): EmailBuilder {
		this.body = body
		return this
	}

	fun attach(file: File): EmailBuilder {
		attachments.add(file)
		return this
	}

	fun send(context: Context) {
		validate()
		val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
		intent.type = "text/plain"
		intent.putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
		intent.putExtra(Intent.EXTRA_SUBJECT, subject)
		intent.putExtra(Intent.EXTRA_TEXT, body)
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		val uris = attachments.mapTo(ArrayList()) { FileProvider.getUriForFile(context, context.packageName + ".fileprovider", it) }
		intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
		context.startActivity(intent)
	}

	private fun validate() {
		checkNotNull(recipient) { "recipient not set" }
		checkNotNull(subject) { "subject not set" }
		checkNotNull(body) { "body not set" }
	}

	companion object {

		fun anEmail(): EmailBuilder {
			return EmailBuilder()
		}
	}
}
