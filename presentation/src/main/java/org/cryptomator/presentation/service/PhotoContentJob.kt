package org.cryptomator.presentation.service

import android.app.job.JobInfo
import android.app.job.JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.provider.MediaStore
import android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
import androidx.annotation.RequiresApi
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.presentation.util.FileUtil
import org.cryptomator.util.file.MimeTypeMap_Factory
import org.cryptomator.util.file.MimeTypes
import timber.log.Timber
import java.lang.String.format
import java.util.*

@RequiresApi(api = Build.VERSION_CODES.N)
class PhotoContentJob : JobService() {

	private val mHandler = Handler()
	private val mWorker: Runnable = Runnable {
		scheduleJob(applicationContext)
		jobFinished(mRunningParams, false)
	}

	private lateinit var mRunningParams: JobParameters

	override fun onStartJob(params: JobParameters): Boolean {
		Timber.tag("PhotoContentJob").i("Job started!")

		val fileUtil = FileUtil(baseContext, MimeTypes(MimeTypeMap_Factory.newInstance()))

		mRunningParams = params
		if (params.triggeredContentAuthorities != null) {
			if (params.triggeredContentUris != null) {
				val ids = getIds(params)
				if (ids != null && ids.size > 0) {
					val selection = buildSelection(ids)
					var cursor: Cursor? = null
					try {
						cursor = contentResolver.query(EXTERNAL_CONTENT_URI, PROJECTION, selection, null, null)
						cursor?.let {
							while (cursor.moveToNext()) {
								val dir = cursor.getString(PROJECTION_DATA)
								try {
									fileUtil.addImageToAutoUploads(dir)
									Timber.tag("PhotoContentJob").i("Added file to UploadList")
									Timber.tag("PhotoContentJob").d(format("Added file to UploadList %s", dir))
								} catch (e: FatalBackendException) {
									Timber.tag("PhotoContentJob").e(e, "Failed to add image to auto upload list")
								}
							}
						} ?: Timber.tag("PhotoContentJob").e("Error: no access to media!")
					} catch (e: SecurityException) {
						Timber.tag("PhotoContentJob").e("Error: no access to media!")
					} finally {
						cursor?.close()
					}
				}
			} else {
				Timber.tag("PhotoContentJob").w("Photos rescan needed!")
				return true
			}
		} else {
			Timber.tag("PhotoContentJob").w("No photos content")
		}

		mHandler.post(mWorker)
		return false
	}

	private fun getIds(params: JobParameters): ArrayList<String>? {
		return params.triggeredContentUris
				?.map { it.pathSegments }
				?.filter { it != null && it.size == EXTERNAL_PATH_SEGMENTS.size + 1 }
				?.mapTo(ArrayList()) { it[it.size - 1] }
	}

	private fun buildSelection(ids: ArrayList<String>): String {
		val selection = StringBuilder()
		ids.indices.forEach { i ->
			if (selection.isNotEmpty()) {
				selection.append(" OR ")
			}
			selection.append(MediaStore.Images.ImageColumns._ID)
			selection.append("='")
			selection.append(ids[i])
			selection.append("'")
		}
		return selection.toString()
	}

	override fun onStopJob(params: JobParameters): Boolean {
		Timber.tag("PhotoContentJob").i("onStopJob called, must stop, reschedule later")
		mHandler.removeCallbacks(mWorker)
		return true
	}

	override fun onDestroy() {
		super.onDestroy()
		Timber.tag("PhotoContentJob").i("Service is destroyed")
	}

	companion object {

		private val MEDIA_URI = Uri.parse("content://" + MediaStore.AUTHORITY + "/")
		internal val EXTERNAL_PATH_SEGMENTS = EXTERNAL_CONTENT_URI.pathSegments
		internal val PROJECTION = arrayOf(MediaStore.Images.ImageColumns._ID, MediaStore.Images.ImageColumns.DATA)

		internal const val PROJECTION_DATA = 1

		private val jobInfo: JobInfo
		private const val PHOTOS_CONTENT_JOB = 23

		init {
			val builder = JobInfo.Builder(PHOTOS_CONTENT_JOB, ComponentName("org.cryptomator", PhotoContentJob::class.java.name))
			builder.addTriggerContentUri(JobInfo.TriggerContentUri(EXTERNAL_CONTENT_URI, FLAG_NOTIFY_FOR_DESCENDANTS))
			builder.addTriggerContentUri(JobInfo.TriggerContentUri(MEDIA_URI, FLAG_NOTIFY_FOR_DESCENDANTS))
			jobInfo = builder.build()
		}

		fun scheduleJob(context: Context) {
			context.getSystemService(JobScheduler::class.java)?.let {
				val result = it.schedule(jobInfo)
				if (result == JobScheduler.RESULT_SUCCESS) {
					Timber.tag("PhotoContentJob").i("Job rescheduled!")
				} else {
					Timber.tag("PhotoContentJob").e("Failed to reschedule job!")
				}
			} ?: Timber.tag("PhotoContentJob").e("Service not found!")
		}

		fun cancelJob(context: Context) {
			context.getSystemService(JobScheduler::class.java)?.let {
				it.cancel(PHOTOS_CONTENT_JOB)
				Timber.tag("PhotoContentJob").i("Job canceled!")
			}
		}
	}
}
