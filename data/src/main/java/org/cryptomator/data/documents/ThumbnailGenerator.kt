package org.cryptomator.data.documents

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import org.cryptomator.domain.VaultPath
import org.cryptomator.util.named
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import timber.log.Timber

private val LOG = Timber.named("ThumbnailGenerator")

private const val THUMBNAIL_SIZE = 256

/**
 * Makes the previews of [ThumbnailStore] one after another, the most recently asked for first, so that the rows on
 * screen get theirs before the ones scrolled past. An image whose local copy is current is read from it. Any other one
 * is downloaded to a temporary file, so that previews don't fill the cache of local copies. The folder of each preview
 * that is done is told to [events]. An image that can't be decoded isn't tried again until its content changes or the
 * process ends. Once all asked for are done, and if any preview was made, the outdated previews are deleted.
 */
class ThumbnailGenerator @JvmOverloads constructor(
	private val store: ThumbnailStore,
	private val metadataCache: DocumentMetadataCache,
	private val contentCache: DocumentContentCache,
	private val events: VaultDocumentEvents,
	private val temporaryDirectory: File,
	private val executor: Executor = Executors.newSingleThreadExecutor { Thread(it, "ThumbnailGenerator").apply { isDaemon = true } },
	private val makeThumbnail: (File) -> ByteArray? = ::jpegThumbnail
) {

	/**
	 * The images to make a preview of, the next one first; guarded by itself.
	 */
	private val requested = ArrayDeque<VaultPath>()
	private var isRunning = false

	/**
	 * The versions of images that could not be decoded, as `<id>-<version>`; guarded by [requested].
	 */
	private val undecodable = HashSet<String>()

	/**
	 * Asks for the previews of the images at [paths], ahead of the ones asked for before, in the order given.
	 */
	fun request(paths: List<VaultPath>) {
		synchronized(requested) {
			paths.asReversed().forEach { path ->
				requested.remove(path)
				requested.addFirst(path)
			}
			if (isRunning) {
				return
			}
			isRunning = true
		}
		executor.execute(::makeRequested)
	}

	private fun makeRequested() {
		var made = false
		while (true) {
			val path = synchronized(requested) {
				requested.removeFirstOrNull() ?: run {
					isRunning = false
					null
				}
			} ?: break
			try {
				made = make(path) || made
			} catch (e: Exception) {
				LOG.w(e, "Making the thumbnail of ${metadataCache.logId(path)} failed")
			}
		}
		if (made) {
			store.evict()
		}
	}

	/**
	 * @return whether a preview was made
	 */
	private fun make(path: VaultPath): Boolean {
		val metadata = metadataCache.cachedDocument(path)?.takeIf { it.isImage } ?: return false
		val version = store.version(metadata) ?: return false
		val key = "${metadata.id}-$version"
		if (store.thumbnail(metadata) != null || synchronized(requested) { key in undecodable }) {
			return false
		}
		val copy = contentCache.currentCopy(path, metadata)
		val image = copy ?: download(path)
		try {
			val thumbnail = makeThumbnail(image)
			if (thumbnail == null) {
				LOG.i("${metadataCache.logId(path)} can't be decoded")
				synchronized(requested) {
					undecodable.add(key)
				}
				return false
			}
			store.put(metadata, version, thumbnail)
		} finally {
			if (copy != null) {
				contentCache.release(copy)
			} else {
				image.delete()
			}
		}
		events.onChanged(requireNotNull(path.parent))
		return true
	}

	private fun download(path: VaultPath): File {
		temporaryDirectory.mkdirs()
		val image = File.createTempFile("thumbnail", ".tmp", temporaryDirectory)
		try {
			contentCache.downloadUnrecorded(path, image)
		} catch (e: Exception) {
			image.delete()
			throw e
		}
		return image
	}
}

/**
 * A JPEG of [image], scaled down so that its shorter side is between [THUMBNAIL_SIZE] and twice that unless it is
 * smaller already, shown upright on white, or null if [image] can't be decoded or encoded, e.g. because it is too long
 * for a JPEG.
 */
private fun jpegThumbnail(image: File): ByteArray? {
	val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
	BitmapFactory.decodeFile(image.path, bounds)
	if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
		return null
	}
	var sampleSize = 1
	while (minOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= THUMBNAIL_SIZE) {
		sampleSize *= 2
	}
	val bitmap = BitmapFactory.decodeFile(image.path, BitmapFactory.Options().apply { inSampleSize = sampleSize }) ?: return null
	val opaque = opaque(upright(bitmap, image))
	return ByteArrayOutputStream().use { jpeg ->
		if (opaque.compress(Bitmap.CompressFormat.JPEG, 85, jpeg)) jpeg.toByteArray() else null
	}
}

/**
 * JPEG has no transparency, its encoder would turn transparent pixels black.
 */
private fun opaque(bitmap: Bitmap): Bitmap {
	if (!bitmap.hasAlpha()) {
		return bitmap
	}
	return Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).apply {
		eraseColor(Color.WHITE)
		Canvas(this).drawBitmap(bitmap, 0f, 0f, null)
	}
}

private fun upright(bitmap: Bitmap, image: File): Bitmap {
	val orientation = try {
		ExifInterface(image.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
	} catch (e: IOException) {
		ExifInterface.ORIENTATION_NORMAL
	}
	val transform = ExifTransform.forOrientation(orientation)
	if (transform.isIdentity) {
		return bitmap
	}
	val matrix = Matrix().apply {
		postRotate(transform.rotationDegrees.toFloat())
		postScale(if (transform.flipHorizontal) -1f else 1f, if (transform.flipVertical) -1f else 1f)
	}
	return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
