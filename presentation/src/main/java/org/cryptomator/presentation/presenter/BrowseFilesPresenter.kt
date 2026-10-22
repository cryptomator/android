package org.cryptomator.presentation.presenter

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.Vault
import org.cryptomator.domain.VaultPath
import org.cryptomator.domain.di.PerView
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException
import org.cryptomator.domain.exception.EmptyDirFileException
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.exception.NoDirFileException
import org.cryptomator.domain.exception.SymLinkException
import org.cryptomator.domain.repository.VaultDocumentListener
import org.cryptomator.domain.repository.VaultDocumentRepository
import org.cryptomator.domain.repository.VaultListing
import org.cryptomator.domain.usecases.CloudFolderRecursiveListing
import org.cryptomator.domain.usecases.CloudNodeRecursiveListing
import org.cryptomator.domain.usecases.DownloadFile
import org.cryptomator.domain.usecases.GetDecryptedCloudForVaultUseCase
import org.cryptomator.domain.usecases.PrepareDownloadFilesUseCase
import org.cryptomator.domain.usecases.ResultRenamed
import org.cryptomator.domain.usecases.cloud.CreateFolderUseCase
import org.cryptomator.domain.usecases.cloud.DeleteNodesUseCase
import org.cryptomator.domain.usecases.cloud.DownloadFilesUseCase
import org.cryptomator.domain.usecases.cloud.DownloadState
import org.cryptomator.domain.usecases.cloud.EndExternalEditingUseCase
import org.cryptomator.domain.usecases.cloud.EnsureVaultThumbnailsUseCase
import org.cryptomator.domain.usecases.cloud.GetCloudListRecursiveUseCase
import org.cryptomator.domain.usecases.cloud.GetCloudListUseCase
import org.cryptomator.domain.usecases.cloud.ListVaultFolderUseCase
import org.cryptomator.domain.usecases.cloud.MoveFilesUseCase
import org.cryptomator.domain.usecases.cloud.MoveFoldersUseCase
import org.cryptomator.domain.usecases.cloud.OpenVaultFilesUseCase
import org.cryptomator.domain.usecases.cloud.Progress
import org.cryptomator.domain.usecases.cloud.RenameFileUseCase
import org.cryptomator.domain.usecases.cloud.RenameFolderUseCase
import org.cryptomator.domain.usecases.cloud.ResumeVaultUploadsUseCase
import org.cryptomator.domain.usecases.cloud.StartExternalEditingUseCase
import org.cryptomator.domain.usecases.cloud.UploadFile
import org.cryptomator.domain.usecases.cloud.UploadFilesUseCase
import org.cryptomator.domain.usecases.cloud.UploadState
import org.cryptomator.domain.usecases.vault.AssertUnlockedUseCase
import org.cryptomator.generator.Callback
import org.cryptomator.generator.InjectIntent
import org.cryptomator.generator.InstanceState
import org.cryptomator.presentation.CryptomatorApp
import org.cryptomator.presentation.R
import org.cryptomator.presentation.docprovider.documentUri
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.intent.BrowseFilesIntent
import org.cryptomator.presentation.intent.ChooseCloudNodeSettings
import org.cryptomator.presentation.intent.IntentBuilder
import org.cryptomator.presentation.intent.Intents
import org.cryptomator.presentation.licensing.LicenseEnforcer
import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.presentation.model.CloudFolderModel
import org.cryptomator.presentation.model.CloudModel
import org.cryptomator.presentation.model.CloudNodeModel
import org.cryptomator.presentation.model.ImagePreviewFilesStore
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.ProgressStateModel
import org.cryptomator.presentation.model.mappers.CloudFileModelMapper
import org.cryptomator.presentation.model.mappers.CloudFolderModelMapper
import org.cryptomator.presentation.model.mappers.CloudNodeModelMapper
import org.cryptomator.presentation.model.mappers.ProgressModelMapper
import org.cryptomator.presentation.model.mappers.ProgressStateModelMapper
import org.cryptomator.presentation.service.OpenWritableFileNotification
import org.cryptomator.presentation.ui.activity.view.BrowseFilesView
import org.cryptomator.presentation.ui.dialog.ChangeFailedDialog
import org.cryptomator.presentation.ui.dialog.ExportCloudFilesDialog
import org.cryptomator.presentation.ui.dialog.FileNameDialog
import org.cryptomator.presentation.util.ContentResolverUtil
import org.cryptomator.presentation.util.FileNameBlacklist
import org.cryptomator.presentation.util.FileUtil
import org.cryptomator.presentation.util.FolderNameBlacklist
import org.cryptomator.presentation.util.ShareFileHelper
import org.cryptomator.presentation.workflow.ActivityResult
import org.cryptomator.presentation.workflow.AddExistingVaultWorkflow
import org.cryptomator.presentation.workflow.AuthenticationExceptionHandler
import org.cryptomator.presentation.workflow.CreateNewVaultWorkflow
import org.cryptomator.presentation.workflow.Workflow
import org.cryptomator.util.ExceptionUtil
import org.cryptomator.util.SharedPreferencesHandler
import org.cryptomator.util.file.FileCacheUtils
import org.cryptomator.util.file.MimeType
import org.cryptomator.util.file.MimeTypes
import java.io.FileNotFoundException
import java.util.function.Supplier
import javax.inject.Inject
import kotlin.reflect.KClass
import timber.log.Timber

@PerView
class BrowseFilesPresenter @Inject constructor( //
	private val getCloudListUseCase: GetCloudListUseCase,  //
	private val createFolderUseCase: CreateFolderUseCase,  //
	private val downloadFilesUseCase: DownloadFilesUseCase,  //
	private val deleteNodesUseCase: DeleteNodesUseCase,  //
	private val uploadFilesUseCase: UploadFilesUseCase,  //
	private val renameFileUseCase: RenameFileUseCase,  //
	private val renameFolderUseCase: RenameFolderUseCase,  //
	private val assertUnlockedUseCase: AssertUnlockedUseCase,  //
	private val fileUtil: FileUtil,  //
	private val fileNameBlacklist: FileNameBlacklist,  //
	private val folderNameBlacklist: FolderNameBlacklist,  //
	private val moveFilesUseCase: MoveFilesUseCase,  //
	private val moveFoldersUseCase: MoveFoldersUseCase,  //
	private val getCloudListRecursiveUseCase: GetCloudListRecursiveUseCase,  //
	private val getDecryptedCloudForVaultUseCase: GetDecryptedCloudForVaultUseCase, //
	private val prepareDownloadFilesUseCase: PrepareDownloadFilesUseCase, //
	private val resumeVaultUploadsUseCase: ResumeVaultUploadsUseCase, //
	private val openVaultFilesUseCase: OpenVaultFilesUseCase, //
	private val startExternalEditingUseCase: StartExternalEditingUseCase, //
	private val endExternalEditingUseCase: EndExternalEditingUseCase, //
	private val listVaultFolderUseCase: ListVaultFolderUseCase, //
	private val ensureVaultThumbnailsUseCase: EnsureVaultThumbnailsUseCase, //
	private val vaultDocumentRepository: VaultDocumentRepository, //
	private val contentResolverUtil: ContentResolverUtil,  //
	private val addExistingVaultWorkflow: AddExistingVaultWorkflow,  //
	private val createNewVaultWorkflow: CreateNewVaultWorkflow,  //
	private val fileCacheUtils: FileCacheUtils,  //
	authenticationExceptionHandler: AuthenticationExceptionHandler,  //
	private val cloudNodeModelMapper: CloudNodeModelMapper,  //
	private val cloudFileModelMapper: CloudFileModelMapper,  //
	private val cloudFolderModelMapper: CloudFolderModelMapper,  //
	private val mimeTypes: MimeTypes,  //
	private val progressStateModelMapper: ProgressStateModelMapper,  //
	private val progressModelMapper: ProgressModelMapper,  //
	private val shareFileHelper: ShareFileHelper,  //
	private val sharedPreferencesHandler: SharedPreferencesHandler,  //
	private val licenseEnforcer: LicenseEnforcer, //
	exceptionMappings: ExceptionHandlers
) : Presenter<BrowseFilesView>(exceptionMappings) {

	private val authenticationExceptionHandler: AuthenticationExceptionHandler
	private lateinit var filesForUpload: MutableMap<String, UploadFile>
	private lateinit var existingFilesForUpload: MutableMap<String, UploadFile>
	private lateinit var downloadFiles: MutableList<DownloadFile>

	private var resumedAfterAuthentication = false

	/**
	 * The vaults to resume the uploads of, each with the files to retry, or `null` for all.
	 */
	private val uploadResumes = SerialRequests<List<Pair<Vault, List<CloudFile>?>>>({ waiting, next -> waiting + next }, ::resumeUploadsNow)

	private var vaultListener: VaultDocumentListener? = null

	/**
	 * Whether to list the folder from the cloud right away.
	 */
	private val vaultFolderListings = SerialRequests<Boolean>({ waiting, next -> waiting || next }, ::listVaultFolderNow)

	/**
	 * The failed listing that has been shown last, so that listing the folder again doesn't show it again.
	 */
	private var shownListingFailure: Throwable? = null

	private val thumbnailRequests = SerialRequests<List<CloudFileModel>>({ _, latest -> latest }, ::ensureThumbnailsNow)

	@InjectIntent
	lateinit var intent: BrowseFilesIntent

	@JvmField
	@InstanceState
	var uploadLocation: CloudFolderModel? = null

	@JvmField
	@InstanceState
	var uriToOpenedFile: Uri? = null

	@JvmField
	@InstanceState
	var finishWhenEditingEnds = false

	@JvmField
	var openWritableFileNotification: OpenWritableFileNotification? = null

	private var isStartingExternalEditing = false

	override fun workflows(): Iterable<Workflow<*>> {
		return listOf(addExistingVaultWorkflow, createNewVaultWorkflow)
	}

	/**
	 * Shows what changed in the vault meanwhile, and keeps showing its changes until paused. It is also called when the
	 * window gets the focus back, without a pause before.
	 */
	override fun resumed() {
		if (activity().isFinishing) {
			return
		}
		val vault = view?.folder?.vault()
		if (vault != null) {
			assertUnlockedUseCase //
				.withVault(vault.toVault()) //
				.run(DefaultResultHandler())
		}
		setRefreshOnBackPressEnabled(enableRefreshOnBackpressSupplier.setInAction(false))
		// the listener keeps the list up to date while the window only lost the focus
		if (vaultListener == null) {
			vaultListener = vaultDocumentChanges.also(vaultDocumentRepository::addListener)
			displayedVaultFolder()?.let { listVaultFolder(forceRefresh = false) }
		}
	}

	override fun paused() {
		vaultListener?.let(vaultDocumentRepository::removeListener)
		vaultListener = null
	}

	private val vaultDocumentChanges = object : VaultDocumentListener {
		override fun onChanged(folder: VaultPath) {
			onUiThread {
				if (folder == displayedVaultPath()) {
					listVaultFolder(forceRefresh = false)
				}
			}
		}

		/**
		 * The browser showed the change already, so a dialog names what failed and passes the reason on.
		 */
		override fun onChangeFailed(path: VaultPath, error: Throwable, userInitiated: Boolean) {
			if (userInitiated) {
				onUiThread {
					if (path.vault.id == displayedVaultPath()?.vault?.id) {
						view?.showDialog(ChangeFailedDialog.newInstance(path.name))
						showError(error)
					}
				}
			}
		}

		override fun onUploadProgress(path: VaultPath, progress: Progress<UploadState>) {
			onUiThread {
				if (path.parent == displayedVaultPath()) {
					view?.renderedCloudNodes()?.find { it is CloudFileModel && it.name == path.name }?.let { file ->
						view?.showProgress(file, progressModelMapper.toModel(progress))
					}
				}
			}
		}
	}

	/**
	 * Runs [block] on the UI thread, unless the presenter is paused by then.
	 */
	private fun onUiThread(block: () -> Unit) {
		activity().runOnUiThread {
			if (!isPaused) {
				block()
			}
		}
	}

	private fun displayedVaultFolder(): CloudFolderModel? {
		return view?.folder?.takeIf { it.vault() != null }
	}

	private fun displayedVaultPath(): VaultPath? {
		return displayedVaultFolder()?.let { vaultDocumentRepository.pathOf(it.toCloudNode()) }
	}

	fun onWindowFocusChanged(hasFocus: Boolean) {
		// the focus may come back before the resume, which then catches up
		if (hasFocus && !isPaused) {
			resumed()
		}
	}

	fun onBackPressed() {
		unsubscribeAll()
	}

	fun onFolderDisplayed(folder: CloudFolderModel) {
		view?.showLoading(true)
		getCloudList(folder)
		view?.updateTitle(folder)
		folder.vault()?.let { resumeUploads(it.toVault(), null) }
	}

	/**
	 * Resumes the uploads of [vault] that failed or were interrupted, so that they don't wait until the vault is opened in
	 * Files. Only the first call per vault after a vault was locked or unlocked does so, see
	 * [VaultDocumentRepository.resumeUploadsOnce]. With [files], only those are retried, each time the user asks for it.
	 * Requests made while uploads are being resumed are carried out afterwards.
	 */
	private fun resumeUploads(vault: Vault, files: List<CloudFile>?) {
		uploadResumes.request(listOf(vault to files))
	}

	private fun resumeUploadsNow(requests: List<Pair<Vault, List<CloudFile>?>>, done: () -> Unit) {
		val (vault, files) = requests.first()
		resumeVaultUploadsUseCase //
			.withVault(vault) //
			.andFiles(files) //
			.run(object : DefaultResultHandler<Void?>() {
				override fun onFinished() {
					if (requests.size > 1) {
						resumeUploadsNow(requests.drop(1), done)
					} else {
						done()
					}
				}
			})
	}

	fun onRetryUploadClicked(cloudFile: CloudFileModel) {
		view?.folder?.vault()?.let { resumeUploads(it.toVault(), listOf(cloudFile.toCloudNode())) }
	}

	/**
	 * Asks for the thumbnails of [files] without waiting for them; they are shown once they are done. While thumbnails
	 * are asked for, only the latest request made meanwhile is carried out afterwards.
	 */
	fun onThumbnailsMissing(files: List<CloudFileModel>) {
		thumbnailRequests.request(files)
	}

	private fun ensureThumbnailsNow(files: List<CloudFileModel>, done: () -> Unit) {
		ensureVaultThumbnailsUseCase //
			.withFiles(files.map { it.toCloudNode() }) //
			.run(object : DefaultResultHandler<Void?>() {
				override fun onFinished() {
					done()
				}
			})
	}

	fun onRefreshTriggered(cloudModel: CloudFolderModel) {
		view?.showLoading(true)
		if (cloudModel.vault() != null) {
			listVaultFolder(forceRefresh = true)
		} else {
			getCloudList(cloudModel)
		}
	}

	/**
	 * Lists the displayed vault folder from the cache, which lists it from the cloud in the background if that is due.
	 * Only one listing runs at a time; a listing asked for meanwhile follows it, and lists the folder displayed by then.
	 * [forceRefresh] lists the folder from the cloud right away and reports a failure also if the cache has an earlier
	 * listing.
	 */
	private fun listVaultFolder(forceRefresh: Boolean) {
		vaultFolderListings.request(forceRefresh)
	}

	private fun listVaultFolderNow(forceRefresh: Boolean, done: () -> Unit) {
		val folder = displayedVaultFolder() ?: return done()
		listVaultFolderUseCase //
			.withFolder(folder.toCloudNode()) //
			.andForceRefresh(forceRefresh) //
			.run(object : DefaultResultHandler<VaultListing>() {
				override fun onSuccess(listing: VaultListing) {
					if (displayedVaultFolder() != folder) {
						return
					}
					showCloudNodesCollectionInView(listing.nodes)
					view?.showLoading(listing.isLoading)
					val failure = listing.error
					if (failure != null && failure !== shownListingFailure) {
						shownListingFailure = failure
						onListingFailed(failure, folder)
					}
				}

				override fun onError(e: Throwable) {
					if (displayedVaultFolder() == folder) {
						view?.showLoading(false)
						onListingFailed(e, folder)
					}
				}

				override fun onFinished() {
					done()
				}
			})
	}

	private fun getCloudList(cloudFolderModel: CloudFolderModel) {
		if (cloudFolderModel.vault() != null) {
			listVaultFolder(forceRefresh = false)
			return
		}
		getCloudListUseCase //
			.withFolder(cloudFolderModel.toCloudNode()) //
			.run(object : DefaultResultHandler<List<CloudNode>>() {
				override fun onSuccess(cloudNodes: List<CloudNode>) {
					if (cloudNodes.isEmpty()) {
						clearCloudList()
					} else {
						showCloudNodesCollectionInView(cloudNodes)
					}
					view?.showLoading(false)
				}

				override fun onError(e: Throwable) {
					view?.showLoading(false)
					onListingFailed(e, cloudFolderModel)
				}
			})
	}

	private fun onListingFailed(e: Throwable, cloudFolderModel: CloudFolderModel) {
		when {
			authenticationExceptionHandler.handleAuthenticationException(this@BrowseFilesPresenter, e, ActivityResultCallbacks.getCloudListAfterAuthentication(cloudFolderModel)) -> {
				resumedAfterAuthentication = true
			}
			e is EmptyDirFileException -> {
				view?.showNoDirFileOrEmptyDialog(e.dirName, e.filePath)
			}
			e is SymLinkException -> {
				view?.showSymLinkDialog()
			}
			e is NoDirFileException -> {
				view?.showNoDirFileOrEmptyDialog(e.cryptoFolderName, e.cloudFolderPath)
			}
			else -> {
				showError(e)
			}
		}
	}

	@Callback(dispatchResultOkOnly = false)
	fun getCloudListAfterAuthentication(result: ActivityResult, cloudFolderModel: CloudFolderModel) {
		if (result.isResultOk) {
			val cloudModel = result.getSingleResult(CloudModel::class.java)
			if (cloudFolderModel.vault() != null) {
				updatedDecryptedCloudFor(Vault.aCopyOf(cloudFolderModel.vault()!!.toVault()).withCloud(cloudModel.toCloud()).build(), cloudFolderModel)
			} else {
				updatePlaintextCloud(cloudFolderModel, cloudModel)
			}
		} else {
			Timber.tag("BrowseFilesPresenter").e("Authentication failed")
		}
	}

	private fun updatePlaintextCloud(cloudFolderModel: CloudFolderModel, updatedCloud: CloudModel) {
		cloudFolderModel.toCloudNode().withCloud(updatedCloud.toCloud())?.let {
			val folder = cloudFolderModelMapper.toModel(it)
			view?.updateActiveFolderDueToAuthenticationProblem(folder)
			getCloudList(folder)
			resumedAfterAuthentication = false
		} ?: throw FatalBackendException("cloudFolderModel with updated Cloud shouldn't be null")
	}

	private fun updatedDecryptedCloudFor(vault: Vault, cloudFolderModel: CloudFolderModel) {
		getDecryptedCloudForVaultUseCase //
			.withVault(vault) //
			.run(object : DefaultResultHandler<Cloud>() {
				override fun onSuccess(cloud: Cloud) {
					val folder = cloudFolderModelMapper.toModel(cloudFolderModel.toCloudNode().withCloud(cloud)!!)
					view?.updateActiveFolderDueToAuthenticationProblem(folder)
					listVaultFolder(forceRefresh = true)
				}

				override fun onFinished() {
					resumedAfterAuthentication = false
				}
			})
	}

	fun onCreateFolderPressed(cloudFolder: CloudFolderModel, folderName: String?) {
		createFolderUseCase //
			.withParent(cloudFolder.toCloudNode()) //
			.andFolderName(folderName) //
			.andOptimistic(cloudFolder.vault() != null) //
			.run(object : DefaultResultHandler<CloudFolder>() {
				// null if an optimistically created folder is gone by now
				override fun onSuccess(cloudFolder: CloudFolder?) {
					cloudFolder?.let { view?.addOrUpdateCloudNode(cloudFolderModelMapper.toModel(it)) }
					view?.closeDialog()
				}
			})
	}

	private fun showCloudNodesCollectionInView(cloudNodes: List<CloudNode>) {
		val cloudNodeModels = cloudNodeModelMapper.toModels(cloudNodes).filter { cloudNode -> !isBlacklistedCloudNode(cloudNode) }
		view?.showCloudNodes(cloudNodeModels)
	}

	private fun isBlacklistedCloudNode(cloudNode: CloudNodeModel<*>): Boolean {
		return if (cloudNode is CloudFileModel) {
			fileNameBlacklist.isBlacklisted(cloudNode)
		} else {
			folderNameBlacklist.isBlacklisted(cloudNode as CloudFolderModel)
		}
	}

	private fun readFiles(cloudFiles: List<CloudFileModel>) {
		// TODO disable rotation
		openVaultFilesUseCase //
			.withFiles(cloudFiles.map { it.toCloudNode() }) //
			.run(object : DefaultProgressAwareResultHandler<List<String>, DownloadState>() {
				override fun onFinished() {
					view?.closeDialog()
				}

				override fun onSuccess(documentIds: List<String>) {
					handleSuccessAfterReadingFiles(cloudFiles, documentIds.map(::documentUri), Intent.ACTION_SEND_MULTIPLE)
				}

				override fun onError(e: Throwable) {
					view?.closeDialog()
					super.onError(e)
				}
			})
	}

	private fun readFilesWithProgress(cloudFiles: List<CloudFileModel>, actionAfterDownload: String) {
		view?.showProgress(
			cloudFiles,  //
			ProgressModel(
				progressStateModelMapper.toModel( //
					DownloadState.download(cloudFiles[0].toCloudNode())
				), 0
			)
		)
		openVaultFilesUseCase //
			.withFiles(cloudFiles.map { it.toCloudNode() }) //
			.run(object : DefaultProgressAwareResultHandler<List<String>, DownloadState>() {
				override fun onFinished() {
					view?.hideProgress(cloudFiles)
				}

				override fun onProgress(progress: Progress<DownloadState>) {
					if (!progress.isOverallComplete) {
						// the progress is about the file in the cloud while the rows show the vault's nodes as the cache
						// knows them, so they are matched by path
						val file = cloudFiles.firstOrNull { it.path == progress.state().file().path } ?: return
						view?.showProgress(file, progressModelMapper.toModel(progress))
					}
				}

				override fun onSuccess(documentIds: List<String>) {
					handleSuccessAfterReadingFiles(cloudFiles, documentIds.map(::documentUri), actionAfterDownload)
				}

				override fun onError(e: Throwable) {
					view?.hideProgress(cloudFiles)
					super.onError(e)
				}
			})
	}

	private fun handleSuccessAfterReadingFiles(files: List<CloudFileModel>, uris: List<Uri>, actionAfterDownload: String) {
		try {
			if (Intent.ACTION_VIEW == actionAfterDownload) {
				viewFile(files[0], uris[0])
			} else {
				if (Intent.ACTION_SEND_MULTIPLE == actionAfterDownload) {
					shareFiles(files, uris)
				} else {
					shareFileHelper.shareFile(this@BrowseFilesPresenter, uris[0], files[0].name)
				}
			}
		} catch (e: ActivityNotFoundException) {
			view?.showFileTypeNotSupportedDialog(files[0])
		}
	}

	private fun exportFile(downloadFiles: List<DownloadFile>) {
		view?.showDialog(ExportCloudFilesDialog.newInstance(downloadFiles.size))
		downloadFilesUseCase //
			.withDownloadFiles(downloadFiles) //
			.run(object : DefaultProgressAwareResultHandler<List<CloudFile>, DownloadState>() {
				override fun onProgress(progress: Progress<DownloadState>) {
					view?.showProgress(progressModelMapper.toModel(progress))
				}

				override fun onSuccess(cloudFiles: List<CloudFile>) {
					view?.closeDialog()
					if (cloudFiles.size > 1) {
						view?.showMessage(R.string.screen_file_browser_msg_files_exported)
					} else {
						view?.showMessage(R.string.screen_file_browser_msg_file_exported)
					}
				}

				override fun onError(e: Throwable) {
					view?.closeDialog()
					super.onError(e)
				}
			})
	}

	private fun uploadFiles(files: List<UploadFile>) {
		uploadLocation?.let {
			val inVault = it.vault() != null
			uploadFilesUseCase //
				.withParent(it.toCloudNode())
				.andFiles(files) //
				.andOptimistic(inVault) //
				.run(object : DefaultProgressAwareResultHandler<List<CloudFile>, UploadState>() {
					override fun onProgress(progress: Progress<UploadState>) {
						view?.showProgress(progressModelMapper.toModel(progress))
						if (progress.isCompleteAndHasState && progress.state().isUpload) {
							onUploadFileCompleted(progress.state().file().name)
						}
					}

					override fun onSuccess(files: List<CloudFile>) {
						// an upload into a vault has no dialog, its rows show up and show its progress once its folder is listed
						if (inVault) {
							uploadLocation = null
						} else {
							files.forEach { file -> view?.addOrUpdateCloudNode(cloudFileModelMapper.toModel(file)) }
							onFileUploadCompleted()
						}
					}

					override fun onError(e: Throwable) {
						onFileUploadError()
						if (ExceptionUtil.contains(e, CloudNodeAlreadyExistsException::class.java)) {
							ExceptionUtil.extract(e, CloudNodeAlreadyExistsException::class.java).get().message
								?.let { message -> onCloudNodeAlreadyExists(message) }
						} else {
							super.onError(e)
						}
					}
				})
		}
	}

	private fun onUploadFileCompleted(name: String) {
		filesForUpload.remove(name)
	}

	private fun onCloudNodeAlreadyExists(fileNameAlreadyExists: String) {
		addToExistingFiles(fileNameAlreadyExists)
		view?.showReplaceDialog(listOf(fileNameAlreadyExists), filesForUpload.size)
	}

	private fun clearCloudList() {
		view?.showCloudNodes(ArrayList())
	}

	/**
	 * A node of a vault is renamed in the list once its folder is listed again, which also covers a rename that has to
	 * wait for an earlier move of the node.
	 */
	fun onRenameCloudNodePressed(cloudNodeModel: CloudNodeModel<*>, newCloudNodeName: String) {
		if (cloudNodeModel is CloudFileModel) {
			renameCloudFile(cloudNodeModel, newCloudNodeName)
		} else {
			renameCloudFolder(cloudNodeModel as CloudFolderModel, newCloudNodeName)
		}
	}

	private fun renameCloudFolder(cloudFolderModel: CloudFolderModel, newCloudFolderName: String) {
		val inVault = cloudFolderModel.vault() != null
		renameFolderUseCase //
			.withFolder(cloudFolderModel.toCloudNode()) //
			.andNewName(newCloudFolderName) //
			.andOptimistic(inVault) //
			.run(object : DefaultResultHandler<ResultRenamed<CloudFolder>>() {
				override fun onSuccess(cloudFolderResultRenamed: ResultRenamed<CloudFolder>) {
					if (!inVault) {
						view?.replaceRenamedCloudNode(cloudNodeModelMapper.toModel(cloudFolderResultRenamed))
					}
					view?.closeDialog()
				}
			})
	}

	private fun renameCloudFile(cloudFileModel: CloudFileModel, newCloudFileName: String) {
		val inVault = view?.folder?.vault() != null
		renameFileUseCase //
			.withFile(cloudFileModel.toCloudNode()) //
			.andNewName(newCloudFileName) //
			.andOptimistic(inVault) //
			.run(object : DefaultResultHandler<ResultRenamed<CloudFile>>() {
				override fun onSuccess(cloudFileResultRenamed: ResultRenamed<CloudFile>) {
					if (!inVault) {
						view?.replaceRenamedCloudNode(cloudNodeModelMapper.toModel(cloudFileResultRenamed))
					}
					view?.closeDialog()
				}
			})
	}

	fun onDeleteCloudNodes(nodes: List<CloudNodeModel<*>>) {
		deleteCloudNode(nodes)
	}

	private fun deleteCloudNode(nodes: List<CloudNodeModel<*>>) {
		val inVault = view?.folder?.vault() != null
		if (!inVault) {
			view?.showProgress(nodes, ProgressModel(ProgressStateModel.DELETION))
		}
		deleteNodesUseCase //
			.withCloudNodes(cloudNodeModelMapper.fromModels(nodes)) //
			.andOptimistic(inVault) //
			.run(object : DefaultResultHandler<List<CloudNode>>() {
				override fun onSuccess(cloudNodes: List<CloudNode>) {
					view?.deleteCloudNodesFromAdapter(cloudNodeModelMapper.toModels(cloudNodes))
				}
			})
	}

	private fun viewFile(cloudFile: CloudFileModel, uri: Uri) {
		val lowerFileName = cloudFile.name.lowercase()
		if (lowerFileName.endsWith(".txt") || lowerFileName.endsWith(".md") || lowerFileName.endsWith(".todo")) {
			val intent = Intents.textEditorIntent()
				.withTextFile(cloudFile)
				.withHubWriteAllowed(licenseEnforcer.hasWriteAccessForVault(view?.folder?.vault()))
				.build(this)
			startIntent(intent)
		} else if (isPreviewImage(cloudFile)) {
			val cloudFileNodes = previewCloudFileNodes
			val imagePreviewStore = ImagePreviewFilesStore( //
				cloudFileNodes,  //
				cloudFileNodes.indexOf(cloudFile)
			)
			startIntent(
				Intents.imagePreviewIntent() //
					.withWithImagePreviewFiles(fileUtil.storeImagePreviewFiles(imagePreviewStore))
			)
		} else {
			viewExternalFile(cloudFile, uri)
		}
	}

	private fun isPreviewImage(cloudFile: CloudFileModel): Boolean {
		return !cloudFile.name.lowercase().endsWith(".gif") && mimeTypes.isImage(cloudFile.name)
	}

	/**
	 * The other app saves through the documents provider, which uploads each save.
	 */
	private fun viewExternalFile(cloudFile: CloudFileModel, uri: Uri) {
		// one start runs at a time and the browser tracks one editor, so an open that finishes while another editor
		// starts or is open is dropped
		if (isStartingExternalEditing || uriToOpenedFile != null) {
			return
		}
		isStartingExternalEditing = true
		val viewFileIntent = Intent(Intent.ACTION_VIEW)
		viewFileIntent.setDataAndType(uri, mimeTypes.fromFilename(cloudFile.name)?.toString())
		var permissionFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
		if (licenseEnforcer.hasWriteAccessForVault(view?.folder?.vault())) {
			permissionFlags = permissionFlags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
		}
		viewFileIntent.addFlags(permissionFlags)
		// the editor is launched once the base is pinned, so that its saves are based on the version it opened
		startExternalEditingUseCase //
			.withDocumentId(DocumentsContract.getDocumentId(uri)) //
			.run(object : DefaultResultHandler<Void?>() {
				override fun onSuccess(result: Void?) {
					// a browser that was left meanwhile has saved its state already, so a recreated one wouldn't
					// know of this editor
					if (isPaused || activity().isFinishing || activity().isDestroyed) {
						unpinBase(uri)
						return
					}
					uriToOpenedFile = uri
					if (sharedPreferencesHandler.keepUnlockedWhileEditing()) {
						openWritableFileNotification = OpenWritableFileNotification(context())
						openWritableFileNotification?.show()
						val cryptomatorApp = activity().application as CryptomatorApp
						cryptomatorApp.suspendLock()
					}
					try {
						requestActivityResult(ActivityResultCallbacks.openFileFinished(), viewFileIntent)
					} catch (e: ActivityNotFoundException) {
						endExternalEditing()
						view?.showFileTypeNotSupportedDialog(cloudFile)
					}
				}

				override fun onError(e: Throwable) {
					unpinBase(uri)
					super.onError(e)
				}

				override fun onFinished() {
					isStartingExternalEditing = false
				}
			})
	}

	/**
	 * Called when the vaults have been locked, e.g. because the screen was turned off. An app that is editing a file can
	 * keep saving it, and the browser stays until the app returns, so that editing ends then.
	 *
	 * @return whether the browser can be closed right away
	 */
	fun onVaultsLocked(): Boolean {
		if (uriToOpenedFile == null) {
			return true
		}
		finishWhenEditingEnds = true
		return false
	}

	@Callback(dispatchResultOkOnly = false)
	fun openFileFinished(result: ActivityResult) {
		endExternalEditing()
	}

	/**
	 * The editor's result doesn't reach a browser that is closed, e.g. because it was left right after launching the
	 * editor, so editing ends then. A browser that is only recreated gets the result.
	 */
	override fun destroyed() {
		if (uriToOpenedFile != null && activity().isFinishing) {
			endExternalEditing()
		}
	}

	private fun endExternalEditing() {
		if (sharedPreferencesHandler.keepUnlockedWhileEditing()) {
			val cryptomatorApp = activity().application as CryptomatorApp
			cryptomatorApp.unSuspendLock()
		}
		hideWritableNotification()

		// the editor's access ends with its task; revoking it would also take the access other apps were given through Files
		uriToOpenedFile?.let(::unpinBase)
		uriToOpenedFile = null
		if (finishWhenEditingEnds) {
			finishWhenEditingEnds = false
			activity().finish()
		}
	}

	/**
	 * Ends what [StartExternalEditingUseCase] began for [uri]. Unlike [endExternalEditing], it leaves the notification and
	 * lock suspension alone, which an open that didn't launch an editor never took.
	 */
	private fun unpinBase(uri: Uri) {
		endExternalEditingUseCase //
			.withDocumentId(DocumentsContract.getDocumentId(uri)) //
			.run(DefaultResultHandler())
	}

	private fun hideWritableNotification() {
		// openWritableFileNotification can not be made serializable because of this, can be null after Activity resumed
		openWritableFileNotification?.hide() ?: OpenWritableFileNotification(context()).hide()
	}

	private val previewCloudFileNodes: ArrayList<CloudFileModel>
		get() {
			val previewCloudFiles = ArrayList<CloudFileModel>()
			view?.renderedCloudNodes()
				?.filterIsInstance<CloudFileModel>()
				?.filterTo(previewCloudFiles, ::isPreviewImage)
			return previewCloudFiles
		}

	private fun shareFiles(shareFiles: List<CloudFileModel>, uris: List<Uri>) {
		val shareFilesIntent = Intent(Intent.ACTION_SEND_MULTIPLE)
		shareFilesIntent.type = combinedMimeType(shareFiles).toString()
		shareFilesIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
		// the chooser only gets the read grant, which it needs to show the names, for the URIs in the clip data
		shareFilesIntent.clipData = ClipData.newRawUri(shareFiles[0].name, uris[0]).apply {
			uris.drop(1).forEach { addItem(ClipData.Item(it)) }
		}
		shareFilesIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		startIntent(Intent.createChooser(shareFilesIntent, getString(R.string.screen_file_browser_share_intent_chooser_title)))
		disableSelectionMode()
	}

	private fun combinedMimeType(shareFiles: List<CloudFileModel>): MimeType {
		var result: MimeType? = null
		shareFiles.forEach { file ->
			val type = mimeTypes.fromFilename(file.name) ?: MimeType.WILDCARD_MIME_TYPE
			result = result?.combine(type) ?: type
		}
		return result ?: MimeType.WILDCARD_MIME_TYPE
	}

	fun onFileClicked(cloudFile: CloudFileModel) {
		readFilesWithProgress(listOf(cloudFile), Intent.ACTION_VIEW)
	}

	fun onShareNodesClicked(nodes: List<CloudNodeModel<*>?>) {
		val filesToShare: MutableList<CloudFileModel> = ArrayList()
		val foldersForRecursiveDirListing: MutableList<CloudFolderModel> = ArrayList()
		nodes.forEach { node ->
			if (node is CloudFileModel) {
				filesToShare.add(node)
			} else if (node is CloudFolderModel) {
				foldersForRecursiveDirListing.add(node)
			}
		}
		collectFolderContentForSharing(foldersForRecursiveDirListing, filesToShare)
		disableSelectionMode()
	}

	private fun collectFolderContentForSharing(folders: List<CloudFolderModel>, filesToShare: List<CloudFileModel>) {
		view?.showProgress(ProgressModel.GENERIC)
		getCloudListRecursiveUseCase //
			.withFolders(cloudFolderModelMapper.fromModels(folders)) //
			.run(object : DefaultResultHandler<CloudNodeRecursiveListing>() {
				override fun onFinished() {
					Timber.tag("BrowseFilesPresenter").d("collectFolderContentForSharing onFinished")
				}

				override fun onSuccess(cloudNodeRecursiveListing: CloudNodeRecursiveListing) {
					Timber.tag("BrowseFilesPresenter").d("cloud node recursive listing")
					prepareSharingOf(cloudNodeRecursiveListing, filesToShare)
				}
			})
	}

	private fun prepareSharingOf(cloudNodeRecursiveListing: CloudNodeRecursiveListing, filesToShare: List<CloudFileModel>) {
		val files: MutableList<CloudFileModel> = ArrayList(filesToShare)
		cloudNodeRecursiveListing.foldersContent.forEach { folderRecursiveListing ->
			files.addAll(prepareFolderContentForSharing(folderRecursiveListing))
		}
		if (files.isEmpty()) {
			view?.showMessage(R.string.screen_file_browser_nothing_to_share)
			view?.closeDialog()
		} else {
			readFiles(files)
		}
	}

	private fun prepareFolderContentForSharing(folderContent: CloudFolderRecursiveListing): List<CloudFileModel> {
		val files: MutableList<CloudFileModel> = ArrayList(cloudFileModelMapper.toModels(folderContent.files))
		folderContent.folders.forEach { folderRecursiveListing ->
			files.addAll(prepareFolderContentForSharing(folderRecursiveListing))
		}
		return files
	}

	fun onShareFolderClicked(cloudFolder: CloudFolderModel?) {
		val nodes = ArrayList<CloudNodeModel<*>?>()
		nodes.add(cloudFolder)
		onShareNodesClicked(nodes)
	}

	fun onShareFileClicked(cloudFile: CloudFileModel) {
		readFilesWithProgress(listOf(cloudFile), Intent.ACTION_SEND)
	}

	private fun moveCloudFile(targetFolder: CloudFolderModel, sourceFiles: List<CloudFileModel>) {
		val inVault = targetFolder.vault() != null
		if (!inVault) {
			view?.showProgress(sourceFiles, ProgressModel(ProgressStateModel.MOVING))
		}
		moveFilesUseCase //
			.withParent(targetFolder.toCloudNode()) //
			.andSourceFiles(cloudFileModelMapper.fromModels(sourceFiles)) //
			.andOptimistic(inVault) //
			.run(object : DefaultResultHandler<List<CloudFile>>() {
				override fun onSuccess(cloudFiles: List<CloudFile>) {
					view?.deleteCloudNodesFromAdapter(sourceFiles)
				}
			})
	}

	private fun moveCloudFolder(targetFolder: CloudFolderModel, sourceFolders: List<CloudFolderModel>) {
		val inVault = targetFolder.vault() != null
		if (!inVault) {
			view?.showProgress(sourceFolders, ProgressModel(ProgressStateModel.MOVING))
		}
		moveFoldersUseCase //
			.withParent(targetFolder.toCloudNode()) //
			.andSourceFolders(cloudFolderModelMapper.fromModels(sourceFolders)) //
			.andOptimistic(inVault) //
			.run(object : DefaultResultHandler<List<CloudFolder>>() {
				override fun onSuccess(cloudFolder: List<CloudFolder>) {
					view?.deleteCloudNodesFromAdapter(sourceFolders)
				}
			})
	}

	private fun prepareSelectedFilesForUpload(fileUris: List<Uri>) {
		filesForUpload = HashMap()
		existingFilesForUpload = HashMap()
		fileUris.forEach { uri ->
			contentResolverUtil.fileName(uri)?.let {
				filesForUpload[it] = createUploadFile(it, uri, false)
			}
		}
		checkForExistingFilesOrUploadFiles()
	}

	private fun checkForExistingFilesOrUploadFiles() {
		view?.let {
			if (hasUsedFileNamesAtLocation(it.renderedCloudNodes())) {
				it.showReplaceDialog(ArrayList(existingFilesForUpload.keys), filesForUpload.size)
			} else {
				uploadFiles(filesForUpload, emptyMap())
			}
		}
	}

	private fun uploadFiles(nonReplacing: Map<String, UploadFile>, replacing: Map<String, UploadFile>) {
		if (nonReplacing.size + replacing.size == 0) {
			return
		}
		// an upload into a vault shows its progress on its row
		if (uploadLocation?.vault() == null) {
			view?.showUploadDialog(nonReplacing.size + replacing.size)
		}
		val filesReadyForUpload: MutableList<UploadFile> = ArrayList()
		filesReadyForUpload.addAll(nonReplacing.values)
		filesReadyForUpload.addAll(replacing.values)
		uploadFiles(filesReadyForUpload)
	}

	private fun hasUsedFileNamesAtLocation(currentCloudNodes: List<CloudNodeModel<*>>): Boolean {
		currentCloudNodes
			.filter { filesForUpload.containsKey(it.name) }
			.forEach {
				if (it is CloudFileModel) {
					addToExistingFiles(it.name)
				} else {
					// remove file when name is used by a folder
					filesForUpload.remove(it.name)
				}
			}
		return existingFilesForUpload.isNotEmpty()
	}

	private fun addToExistingFiles(nodeName: String) {
		existingFilesForUpload[nodeName] = replacingUploadFile(nodeName)
	}

	private fun replacingUploadFile(nodeName: String): UploadFile {
		return UploadFile.aCopyOf( //
			filesForUpload[nodeName]
		) //
			.thatIsReplacing(true) //
			.build()
	}

	fun uploadFilesAndReplaceExistingFiles() {
		differencesOfUploadAndExistingFiles()
		uploadFiles(filesForUpload, existingFilesForUpload)
	}

	fun uploadFilesAndSkipExistingFiles() {
		differencesOfUploadAndExistingFiles()
		uploadFiles(filesForUpload, emptyMap())
	}

	private fun onFileUploadCompleted() {
		view?.showProgress(ProgressModel.COMPLETED)
		uploadLocation = null
	}

	private fun onFileUploadError() {
		view?.closeDialog()
	}

	private fun differencesOfUploadAndExistingFiles() {
		filesForUpload.keys.removeAll(existingFilesForUpload.keys)
	}

	fun onFolderChosen(chosenFolder: CloudFolderModel?) {
		if (view?.hasExcludedFolder() == true) {
			view?.showMessage(context().getString(R.string.error_file_or_folder_exists))
		} else {
			finishWithResult(chosenFolder)
		}
	}

	fun onFileChosen(cloudFile: CloudFileModel?) {
		finishWithResult(cloudFile)
	}

	fun onFolderClicked(cloudFolderModel: CloudFolderModel) {
		unsubscribeAll()
		view?.navigateTo(cloudFolderModel)
	}

	fun onExportFileClicked(cloudFile: CloudFileModel) {
		exportFileToUserSelectedLocation(cloudFile)
	}

	fun onExportNodesClicked(selectedCloudFiles: ArrayList<CloudNodeModel<*>>) {
		exportNodesToUserSelectedLocation(selectedCloudFiles)
	}

	private fun exportFileToUserSelectedLocation(fileToExport: CloudFileModel) {
		val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
		intent.addCategory(Intent.CATEGORY_OPENABLE)
		intent.type = "*/*"
		intent.putExtra(Intent.EXTRA_TITLE, fileToExport.name)
		requestActivityResult(ActivityResultCallbacks.exportFileToUserSelectedLocation(fileToExport), intent)
	}

	private fun exportNodesToUserSelectedLocation(nodesToExport: ArrayList<CloudNodeModel<*>>) {
		try {
			requestActivityResult( //
				ActivityResultCallbacks.pickedLocalStorageLocationForBrowsingFiles(nodesToExport),  //
				Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
			)
		} catch (exception: ActivityNotFoundException) {
			Toast //
				.makeText( //
					activity().applicationContext,  //
					context().getText(R.string.screen_cloud_local_error_no_content_provider),  //
					Toast.LENGTH_SHORT
				) //
				.show()
			Timber.tag("BrowseFilesPresenter").e(exception, "Export file: No ContentProvider on system")
		}
	}

	@Callback
	fun pickedLocalStorageLocationForBrowsingFiles(
		result: ActivityResult,  //
		nodesToExport: ArrayList<CloudNodeModel<*>>
	) {
		val pickedLocalStorageLocation = DocumentsContract.buildChildDocumentsUriUsingTree( //
			Uri.parse(result.intent().data.toString()),  //
			DocumentsContract.getTreeDocumentId( //
				Uri.parse(result.intent().data.toString())
			)
		)
		collectNodesToExport(
			pickedLocalStorageLocation,  //
			nodesToExport
		)
		disableSelectionMode()
	}

	private fun collectNodesToExport(
		parentUri: Uri,  //
		nodesToExport: List<CloudNodeModel<*>>
	) {
		val filesToExport: MutableList<CloudFileModel> = ArrayList()
		val foldersForRecursiveDirListing: MutableList<CloudFolderModel> = ArrayList()
		nodesToExport.forEach { node ->
			if (node is CloudFileModel) {
				filesToExport.add(node)
			} else {
				foldersForRecursiveDirListing.add(node as CloudFolderModel)
			}
		}
		collectFolderContentForExport(parentUri, foldersForRecursiveDirListing, filesToExport)
	}

	private fun collectFolderContentForExport(
		parentUri: Uri, folders: List<CloudFolderModel>,  //
		filesToExport: List<CloudFileModel>
	) {
		view?.showProgress(ProgressModel.GENERIC)
		getCloudListRecursiveUseCase //
			.withFolders(cloudFolderModelMapper.fromModels(folders)) //
			.run(object : DefaultResultHandler<CloudNodeRecursiveListing>() {
				override fun onFinished() {
					Timber.tag("BrowseFilesPresenter").d("collectFolderContentForExport onFinished")
				}

				override fun onSuccess(cloudNodeRecursiveListing: CloudNodeRecursiveListing) {
					Timber.tag("BrowseFilesPresenter").d("cloud node recursive listing")
					prepareExportingOf(parentUri, filesToExport, cloudNodeRecursiveListing)
				}
			})
	}

	private fun prepareExportingOf(parentUri: Uri, filesToExport: List<CloudFileModel>, cloudNodeRecursiveListing: CloudNodeRecursiveListing) {
		view?.showProgress(ProgressModel.GENERIC)
		downloadFiles = ArrayList()
		prepareDownloadFilesUseCase
			.withFilesToExport(cloudFileModelMapper.fromModels(filesToExport))
			.andParentUri(parentUri)
			.andCloudNodeRecursiveListing(cloudNodeRecursiveListing)
			.run(object : DefaultResultHandler<List<DownloadFile>>() {
				override fun onSuccess(prepareDownloadFiles: List<DownloadFile>) {
					view?.showProgress(ProgressModel.COMPLETED)
					downloadFiles = prepareDownloadFiles.toMutableList()
					if (downloadFiles.isEmpty()) {
						view?.showMessage(R.string.screen_file_browser_nothing_to_export)
						view?.closeDialog()
					} else {
						exportFile(downloadFiles)
					}
				}

				override fun onError(e: Throwable) {
					view?.showProgress(ProgressModel.COMPLETED)
					showError(e)
					disableSelectionMode()
					throw FatalBackendException(e)
				}
			})
	}

	@Callback
	fun exportFileToUserSelectedLocation(result: ActivityResult, fileToExport: CloudFileModel) {
		try {
			val downloadFile = DownloadFile.Builder() //
				.setDownloadFile(fileToExport.toCloudNode()) //
				.setDataSink(contentResolverUtil.openOutputStream(Uri.parse(result.intent().dataString))) //
				.build()
			exportFile(listOf(downloadFile))
		} catch (e: FileNotFoundException) {
			showError(e)
		}
	}

	fun onUploadFilesClicked(folder: CloudFolderModel) {
		uploadLocation = folder
		var intent = Intent(Intent.ACTION_GET_CONTENT)
		intent.addCategory(Intent.CATEGORY_OPENABLE)
		intent.type = "*/*"
		intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
		intent = Intent.createChooser(intent, context().getString(R.string.screen_file_browser_upload_files_chooser_title))
		requestActivityResult(ActivityResultCallbacks.selectedFiles(), intent)
	}

	fun onUploadCanceled() {
		uploadFilesUseCase.cancel()
	}

	@Callback
	fun selectedFiles(result: ActivityResult) {
		val fileUris = getFileUrisFromIntent(result.intent())
		prepareSelectedFilesForUpload(fileUris)
	}

	private fun getFileUrisFromIntent(intent: Intent): List<Uri> {
		val fileUris: MutableList<Uri> = ArrayList()
		intent.clipData?.let {
			(0 until it.itemCount).forEach { i ->
				fileUris.add(it.getItemAt(i).uri)
			}
		} ?: intent.data?.let {
			fileUris.add(it)
		}
		return fileUris
	}

	private fun moveIntentFor(parent: CloudFolderModel, sourceNodes: List<CloudNodeModel<*>>): IntentBuilder {
		val foldersToMove = nodesFor(sourceNodes, CloudFolderModel::class) as List<CloudFolderModel>
		return Intents.browseFilesIntent() //
			.withTitle(effectiveMoveTitle()) //
			.withFolder(parent) //
			.withChooseCloudNodeSettings( //
				ChooseCloudNodeSettings.chooseCloudNodeSettings() //
					.withExtraTitle(effectiveMoveExtraTitle(sourceNodes)) //
					.withButtonText(context().getString(R.string.screen_file_browser_move_button_text)) //
					.withNavigationMode(ChooseCloudNodeSettings.NavigationMode.MOVE_CLOUD_NODE) //
					.withExtraToolbarIcon(R.drawable.ic_clear) //
					.selectingFoldersNotContaining(sourceNodes.map { node -> node.name }) //
					.excludingFolder(if (foldersToMove.isEmpty()) null else foldersToMove) //
					.build()
			)
	}

	private fun effectiveMoveTitle(): String {
		return if (intent.folder().name.isEmpty()) //
			intent.title() else  //
			intent.folder().name
	}

	private fun effectiveMoveExtraTitle(sourceNodes: List<CloudNodeModel<*>>): String {
		return context().resources.getQuantityString(R.plurals.screen_file_browser_subtitle_move, sourceNodes.size, sourceNodes[0].name, sourceNodes.size)
	}

	private fun nodesFor(nodes: List<CloudNodeModel<*>>, nodeTypeClass: KClass<out CloudNodeModel<*>>): List<CloudNodeModel<*>> {
		return nodes.filter { node -> nodeTypeClass.isInstance(node) }
	}

	fun onMoveNodeClicked(parent: CloudFolderModel, nodeToMove: CloudNodeModel<*>) {
		val cloudNodeModels = ArrayList<CloudNodeModel<*>>()
		cloudNodeModels.add(nodeToMove)
		onMoveNodesClicked(parent, cloudNodeModels)
	}

	fun onMoveNodesClicked(parent: CloudFolderModel, nodesToMove: ArrayList<CloudNodeModel<*>>) {
		requestActivityResult(
			ActivityResultCallbacks.moveNodes(nodesToMove),  //
			moveIntentFor(parent, nodesToMove)
		)
	}

	@Callback
	fun moveNodes(result: ActivityResult, nodesToMove: ArrayList<CloudNodeModel<*>>) {
		setRefreshOnBackPressEnabled(enableRefreshOnBackpressSupplier.setInAction(true))
		val targetFolder = result.getSingleResult(CloudFolderModel::class.java)
		moveCloudFile(targetFolder, nodesFor(nodesToMove, CloudFileModel::class) as List<CloudFileModel>)
		moveCloudFolder(targetFolder, nodesFor(nodesToMove, CloudFolderModel::class) as List<CloudFolderModel>)
		disableSelectionMode()
	}

	fun disableSelectionMode() {
		setRefreshOnBackPressEnabled(enableRefreshOnBackpressSupplier.setInSelectionMode(false))
		view?.disableSelectionMode()
	}

	fun onCreateNewTextFileClicked() {
		view?.showDialog(FileNameDialog())
	}

	fun onOpenWithTextFileClicked(textFile: CloudFileModel, newlyCreated: Boolean, internalEditor: Boolean) {
		openVaultFilesUseCase //
			.withFiles(listOf(textFile.toCloudNode())) //
			.run(object : DefaultProgressAwareResultHandler<List<String>, DownloadState>() {
				override fun onProgress(progress: Progress<DownloadState>) {
					if (!newlyCreated) {
						view?.showProgress(textFile, progressModelMapper.toModel(progress))
					}
				}

				override fun onSuccess(documentIds: List<String>) {
					if (!newlyCreated) {
						view?.hideProgress(textFile)
					}
					if (internalEditor) {
						val editorIntent = Intents.textEditorIntent()
							.withTextFile(textFile)
							.withHubWriteAllowed(licenseEnforcer.hasWriteAccessForVault(view?.folder?.vault()))
							.build(this@BrowseFilesPresenter)
						startIntent(editorIntent)
					} else {
						viewExternalFile(textFile, documentUri(documentIds[0]))
					}
				}

				override fun onError(e: Throwable) {
					super.onError(e)
					view?.hideProgress(textFile)
				}
			})
	}

	fun onCreateNewTextFileClicked(parent: CloudFolderModel, fileName: String) {
		view?.showProgress(ProgressModel(ProgressStateModel.CREATING_TEXT_FILE))
		val tmpFileUri = fileCacheUtils.tmpFile().empty().create()
		uploadFilesUseCase //
			.withParent(parent.toCloudNode()) //
			.andFiles(listOf(createUploadFile(fileName, tmpFileUri, false))) //
			.run(object : DefaultProgressAwareResultHandler<List<CloudFile>, UploadState>() {
				override fun onSuccess(cloudFile: List<CloudFile>) {
					val cloudFileModel = cloudFileModelMapper.toModel(cloudFile[0])
					view?.addOrUpdateCloudNode(cloudFileModel)
					onOpenWithTextFileClicked(cloudFileModel, newlyCreated = true, internalEditor = true)
					view?.closeDialog()
				}
			})
	}

	private fun createUploadFile(fileName: String, uri: Uri, replacing: Boolean): UploadFile {
		return UploadFile.anUploadFile() //
			.withFileName(fileName) //
			.withDataSource(UriBasedDataSource.from(uri)) //
			.thatIsReplacing(replacing) //
			.build()
	}

	fun onFolderRedisplayed(folder: CloudFolderModel) {
		view?.updateTitle(folder)
	}

	fun onAddContentClicked() {
		view?.showAddContentDialog()
	}

	fun onNodeSettingsClicked(node: CloudNodeModel<*>) {
		view?.showNodeSettingsDialog(node)
	}

	fun onSelectionModeActivated() {
		setRefreshOnBackPressEnabled(enableRefreshOnBackpressSupplier.setInSelectionMode(true))
		view?.enableSelectionMode()
	}

	fun onSelectedNodesChanged(selectedNodes: Int) {
		if (selectedNodes == 0) {
			view?.disableGeneralSelectionActions()
		} else {
			view?.enableGeneralSelectionActions()
		}
		view?.updateSelectionTitle(selectedNodes)
	}

	fun onFolderReloadContent(folder: CloudFolderModel) {
		if (!resumedAfterAuthentication) {
			getCloudList(folder)
		}
	}

	fun onExportFolderClicked(cloudFolder: CloudFolderModel) {
		val nodes = ArrayList<CloudNodeModel<*>>()
		nodes.add(cloudFolder)
		onExportNodesClicked(nodes)
	}

	fun exportNodesCanceled() {
		downloadFilesUseCase.unsubscribe()
		view?.closeDialog()
	}

	fun invalidateOptionsMenu() {
		activity().invalidateOptionsMenu()
	}

	private val enableRefreshOnBackpressSupplier = RefreshSupplier()

	class RefreshSupplier : Supplier<Boolean> {

		private var inSelectionMode = false
		private var inAction = false
		fun setInAction(inAction: Boolean): RefreshSupplier {
			this.inAction = inAction
			return this
		}

		fun setInSelectionMode(inSelectionMode: Boolean): RefreshSupplier {
			this.inSelectionMode = inSelectionMode
			return this
		}

		override fun get(): Boolean {
			return !(inSelectionMode || inAction)
		}
	}

	companion object {

		const val OPEN_FILE_FINISHED = 12
	}

	init {
		unsubscribeOnDestroy( //
			getCloudListUseCase,  //
			createFolderUseCase,  //
			downloadFilesUseCase,  //
			deleteNodesUseCase,  //
			uploadFilesUseCase,  //
			renameFileUseCase,  //
			renameFolderUseCase,  //
			moveFilesUseCase,  //
			moveFoldersUseCase, //
			getDecryptedCloudForVaultUseCase, //
			prepareDownloadFilesUseCase, //
			openVaultFilesUseCase
		)
		// listVaultFolderUseCase, ensureVaultThumbnailsUseCase and resumeVaultUploadsUseCase are left running when
		// navigating, since unsubscribing would skip the onFinished their SerialRequests waits for.
		// startExternalEditingUseCase is left running, too, since editing has to end also if the browser was left.
		this.authenticationExceptionHandler = authenticationExceptionHandler
	}
}
