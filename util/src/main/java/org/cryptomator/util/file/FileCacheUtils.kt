package org.cryptomator.util.file

import android.content.Context
import android.net.Uri
import org.cryptomator.util.Encodings
import java.io.*
import java.util.*
import javax.inject.Inject

class FileCacheUtils @Inject constructor(context: Context) {

	private val cacheDir: File = context.cacheDir

	@Throws(IOException::class)
	fun read(`in`: InputStream): String {
		BufferedReader(InputStreamReader(`in`, Encodings.UTF_8)).use { reader ->
			StringWriter().use { writer ->
				val buffer = CharArray(1024 * 4)
				var line: Int
				while (reader.read(buffer).also { line = it } != EOF) {
					writer.write(buffer, 0, line)
				}
				return writer.toString()
			}
		}
	}

	fun deleteTmpFile(uri: Uri) {
		uri.path?.let { File(it).delete() }
	}

	fun tmpFile(): TmpFileBuilder {
		return try {
			TmpFileBuilder(File.createTempFile(UUID.randomUUID().toString(), ".tmp", cacheDir))
		} catch (e: IOException) {
			throw IllegalStateException("Create tmp file ", e)
		}
	}

	inner class TmpFileBuilder internal constructor(private val tmpFile: File) {

		private var content: String? = null

		fun withContent(text: String?): TmpFileBuilder {
			content = text
			return this
		}

		fun empty(): TmpFileBuilder {
			content = ""
			return this
		}

		fun create(): Uri {
			writeToFile(tmpFile, content)
			return Uri.fromFile(tmpFile)
		}

		private fun writeToFile(tmpFile: File, content: String?) {
			open(tmpFile).use { writer -> writer.print(content) }
		}

		private fun open(tmpFile: File): PrintWriter {
			return try {
				PrintWriter(OutputStreamWriter(FileOutputStream(tmpFile), Encodings.UTF_8))
			} catch (e: FileNotFoundException) {
				throw IllegalStateException("Opening ", e)
			}
		}
	}

	companion object {
		private const val EOF = -1
	}

}
