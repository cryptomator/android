package org.cryptomator.presentation.presenter

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import org.cryptomator.data.cloud.crypto.CryptoFolder
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.Vault
import org.cryptomator.domain.di.PerView
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException
import org.cryptomator.domain.exception.EmptyDirFileException
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.exception.NoDirFileException
import org.cryptomator.domain.exception.NoSuchCloudFileException
import org.cryptomator.domain.exception.SymLinkException
import org.cryptomator.domain.usecases.CloudFolderRecursiveListing
import org.cryptomator.domain.usecases.CloudNodeRecursiveListing
import org.cryptomator.domain.usecases.CopyDataUseCase
import org.cryptomator.domain.usecases.DownloadFile
import org.cryptomator.domain.usecases.GetDecryptedCloudForVaultUseCase
import org.cryptomator.domain.usecases.ResultRenamed
import org.cryptomator.domain.usecases.cloud.CreateFolderUseCase
import org.cryptomator.domain.usecases.cloud.DeleteNodesUseCase
import org.cryptomator.domain.usecases.cloud.DownloadFilesUseCase
import org.cryptomator.domain.usecases.cloud.DownloadState
import org.cryptomator.domain.usecases.cloud.GetCloudListRecursiveUseCase
import org.cryptomator.domain.usecases.cloud.GetCloudListUseCase
import org.cryptomator.domain.usecases.cloud.MoveFilesUseCase
import org.cryptomator.domain.usecases.cloud.MoveFoldersUseCase
import org.cryptomator.domain.usecases.cloud.Progress
import org.cryptomator.domain.usecases.cloud.RenameFileUseCase
import org.cryptomator.domain.usecases.cloud.RenameFolderUseCase
import org.cryptomator.domain.usecases.cloud.UploadFile
import org.cryptomator.domain.usecases.cloud.UploadFilesUseCase
import org.cryptomator.domain.usecases.cloud.UploadState
import org.cryptomator.domain.usecases.vault.AssertUnlockedUseCase
import org.cryptomator.generator.Callback
import org.cryptomator.generator.InjectIntent
import org.cryptomator.generator.InstanceState
import org.cryptomator.presentation.CryptomatorApp
import org.cryptomator.presentation.R
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.exception.IllegalFileNameException
import org.cryptomator.presentation.intent.BrowseFilesIntent
import org.cryptomator.presentation.intent.ChooseCloudNodeSettings
import org.cryptomator.presentation.intent.IntentBuilder
import org.cryptomator.presentation.intent.Intents
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
import org.cryptomator.presentation.ui.dialog.ExportCloudFilesDialog
import org.cryptomator.presentation.ui.dialog.FileNameDialog
import org.cryptomator.presentation.util.ContentResolverUtil
import org.cryptomator.presentation.util.DownloadFileUtil
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
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.Serializable
import java.security.DigestInputStream
import java.security.MessageDigest
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
	private val copyDataUseCase: CopyDataUseCase,  //
	private val assertUnlockedUseCase: AssertUnlockedUseCase,  //
	private val fileUtil: FileUtil,  //
	private val fileNameBlacklist: FileNameBlacklist,  //
	private val folderNameBlacklist: FolderNameBlacklist,  //
	private val moveFilesUseCase: MoveFilesUseCase,  //
	private val moveFoldersUseCase: MoveFoldersUseCase,  //
	private val getCloudListRecursiveUseCase: GetCloudListRecursiveUseCase,  //
	private val getDecryptedCloudForVaultUseCase: GetDecryptedCloudForVaultUseCase, //
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
	private val downloadFileUtil: DownloadFileUtil,  //
	private val sharedPreferencesHandler: SharedPreferencesHandler,  //
	exceptionMappings: ExceptionHandlers
) : Presenter<BrowseFilesView>(exceptionMappings) {

	private val authenticationExceptionHandler: AuthenticationExceptionHandler
	private lateinit var filesForUpload: MutableMap<String, UploadFile>
	private lateinit var existingFilesForUpload: MutableMap<String, UploadFile>
	private lateinit var downloadFiles: MutableList<DownloadFile>

	private var resumedAfterAuthentication = false

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
	var openedCloudFile: CloudFileModel? = null

	@JvmField
	@InstanceState
	var openedCloudFileMd5: ByteArray? = null

	@JvmField
	var openWritableFileNotification: OpenWritableFileNotification? = null

	override fun workflows(): Iterable<Workflow<*>> {
		return listOf(addExistingVaultWorkflow, createNewVaultWorkflow)
	}

	override fun resumed() {
		val vault = view?.folder?.vault()
		if (vault != null) {
			assertUnlockedUseCase //
				.withVault(vault.toVault()) //
				.run(DefaultResultHandler())
		}
		setRefreshOnBackPressEnabled(enableRefreshOnBackpressSupplier.setInAction(false))
	}

	fun onWindowFocusChanged(hasFocus: Boolean) {
		if (hasFocus) {
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
	}

	fun onRefreshTriggered(cloudModel: CloudFolderModel) {
		view?.showLoading(true)
		getCloudList(cloudModel)
	}

	private fun getCloudList(cloudFolderModel: CloudFolderModel) {
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
					when {
						authenticationExceptionHandler.handleAuthenticationException(this@BrowseFilesPresenter, e, ActivityResultCallbacks.getCloudListAfterAuthentication(cloudFolderModel)) -> {
							resumedAfterAuthentication = true
							return
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
							super.onError(e)
						}
					}
				}
			})
	}

	@Callback(dispatchResultOkOnly = false)
	fun getCloudListAfterAuthentication(result: ActivityResult, cloudFolderModel: CloudFolderModel) {
		if(result.isResultOk) {
			val cloudModel = result.getSingleResult(CloudModel::class.java)
			val cloudNode = cloudFolderModel.toCloudNode()
			if (cloudNode is CryptoFolder) {
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
					getCloudList(folder)
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
			.run(object : DefaultResultHandler<CloudFolder>() {
				override fun onSuccess(cloudFolder: CloudFolder) {
					view?.addOrUpdateCloudNode(cloudFolderModelMapper.toModel(cloudFolder))
					view?.closeDialog()
				}
			})
	}

	private fun copyFile(downloadFiles: List<DownloadFile>) {
		downloadFiles.forEach { downloadFile ->
			try {
				val source = FileInputStream(fileUtil.fileFor(cloudFileModelMapper.toModel(downloadFile.downloadFile)))
				copyDataUseCase //
					.withSource(source) //
					.andTarget(downloadFile.dataSink) //
					.run(object : DefaultResultHandler<Void>() {
						override fun onSuccess(t: Void?) {
							view?.showMessage(R.string.screen_file_browser_msg_file_exported)
						}
						override fun onFinished() {
							source.close()
						}
					})
			} catch (e: FileNotFoundException) {
				showError(e)
			}
		}
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
		downloadFilesUseCase //
			.withDownloadFiles(downloadFileUtil.createDownloadFilesFor(this, cloudFiles)) //
			.run(object : DefaultProgressAwareResultHandler<List<CloudFile>, DownloadState>() {
				override fun onFinished() {
					view?.closeDialog()
				}

				override fun onSuccess(files: List<CloudFile>) {
					handleSuccessAfterReadingFiles(files, Intent.ACTION_SEND_MULTIPLE)
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
		downloadFilesUseCase //
			.withDownloadFiles(downloadFileUtil.createDownloadFilesFor(this, cloudFiles)) //
			.run(object : DefaultProgressAwareResultHandler<List<CloudFile>, DownloadState>() {
				override fun onFinished() {
					view?.hideProgress(cloudFiles)
				}

				override fun onProgress(progress: Progress<DownloadState>) {
					if (!progress.isOverallComplete) {
						view?.showProgress(
							cloudFileModelMapper.toModel(progress.state().file()),  //
							progressModelMapper.toModel(progress)
						)
					}
				}

				override fun onSuccess(files: List<CloudFile>) {
					handleSuccessAfterReadingFiles(files, actionAfterDownload)
				}

				override fun onError(e: Throwable) {
					view?.hideProgress(cloudFiles)
					super.onError(e)
				}
			})
	}

	private fun handleSuccessAfterReadingFiles(files: List<CloudFile>, actionAfterDownload: String) {
		try {
			if (Intent.ACTION_VIEW == actionAfterDownload) {
				viewFile(cloudFileModelMapper.toModel(files[0]))
			} else {
				if (Intent.ACTION_SEND_MULTIPLE == actionAfterDownload) {
					shareFiles(cloudFileModelMapper.toModels(files))
				} else {
					shareFileHelper.shareFile(this@BrowseFilesPresenter, cloudFileModelMapper.toModel(files[0]))
				}
			}
		} catch (e: ActivityNotFoundException) {
			view?.showFileTypeNotSupportedDialog(cloudFileModelMapper.toModel(files[0]))
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
			uploadFilesUseCase //
				.withParent(it.toCloudNode())
				.andFiles(files) //
				.run(object : DefaultProgressAwareResultHandler<List<CloudFile>, UploadState>() {
					override fun onProgress(progress: Progress<UploadState>) {
						view?.showProgress(progressModelMapper.toModel(progress))
						if (progress.isCompleteAndHasState && progress.state().isUpload) {
							onUploadFileCompleted(progress.state().file().name)
						}
					}

					override fun onSuccess(files: List<CloudFile>) {
						files.forEach { file -> view?.addOrUpdateCloudNode(cloudFileModelMapper.toModel(file)) }
						onFileUploadCompleted()
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

	fun onRenameCloudNodePressed(cloudNodeModel: CloudNodeModel<*>, newCloudNodeName: String) {
		if (cloudNodeModel is CloudFileModel) {
			renameCloudFile(cloudNodeModel, newCloudNodeName)
		} else {
			renameCloudFolder(cloudNodeModel as CloudFolderModel, newCloudNodeName)
		}
	}

	private fun renameCloudFolder(cloudFolderModel: CloudFolderModel, newCloudFolderName: String) {
		renameFolderUseCase //
			.withFolder(cloudFolderModel.toCloudNode()) //
			.andNewName(newCloudFolderName) //
			.run(object : DefaultResultHandler<ResultRenamed<CloudFolder>>() {
				override fun onSuccess(cloudFolderResultRenamed: ResultRenamed<CloudFolder>) {
					view?.replaceRenamedCloudNode(cloudNodeModelMapper.toModel(cloudFolderResultRenamed))
					view?.closeDialog()
				}
			})
	}

	private fun renameCloudFile(cloudFileModel: CloudFileModel, newCloudFileName: String) {
		renameFileUseCase //
			.withFile(cloudFileModel.toCloudNode()) //
			.andNewName(newCloudFileName) //
			.run(object : DefaultResultHandler<ResultRenamed<CloudFile>>() {
				override fun onSuccess(cloudFileResultRenamed: ResultRenamed<CloudFile>) {
					view?.replaceRenamedCloudNode(cloudNodeModelMapper.toModel(cloudFileResultRenamed))
					view?.closeDialog()
				}
			})
	}

	fun onDeleteCloudNodes(nodes: List<CloudNodeModel<*>>) {
		deleteCloudNode(nodes)
	}

	private fun deleteCloudNode(nodes: List<CloudNodeModel<*>>) {
		view?.showProgress(nodes, ProgressModel(ProgressStateModel.DELETION))
		deleteNodesUseCase //
			.withCloudNodes(cloudNodeModelMapper.fromModels(nodes)) //
			.run(object : DefaultResultHandler<List<CloudNode>>() {
				override fun onSuccess(cloudNodes: List<CloudNode>) {
					view?.deleteCloudNodesFromAdapter(cloudNodeModelMapper.toModels(cloudNodes))
				}
			})
	}

	private fun viewFile(cloudFile: CloudFileModel) {
		val lowerFileName = cloudFile.name.lowercase()
		if (lowerFileName.endsWith(".txt") || lowerFileName.endsWith(".md") || lowerFileName.endsWith(".todo")) {
			startIntent(
				Intents.textEditorIntent() //
					.withTextFile(cloudFile)
			)
		} else if (!lowerFileName.endsWith(".gif") && isImageMediaType(cloudFile.name)) {
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
			viewExternalFile(cloudFile)
		}
	}

	private fun isImageMediaType(filename: String): Boolean {
		return (mimeTypes.fromFilename(filename) ?: MimeType.WILDCARD_MIME_TYPE).mediatype == "image"
	}

	private fun viewExternalFile(cloudFile: CloudFileModel) {
		val viewFileIntent = Intent(Intent.ACTION_VIEW)
		fileUtil.contentUriFor(cloudFile).let {
			uriToOpenedFile = it
			openedCloudFile = cloudFile
			openedCloudFileMd5 = calculateDigestFromUri(it)
			viewFileIntent.setDataAndType( //
				uriToOpenedFile,  //
				mimeTypes.fromFilename(cloudFile.name)?.toString()
			)
			viewFileIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
			if (sharedPreferencesHandler.keepUnlockedWhileEditing()) {
				openWritableFileNotification = OpenWritableFileNotification(context(), it)
				openWritableFileNotification?.show()
				val cryptomatorApp = activity().application as CryptomatorApp
				cryptomatorApp.suspendLock()
			}
			activity().startActivityForResult(viewFileIntent, OPEN_FILE_FINISHED)
		}
	}

	private val previewCloudFileNodes: ArrayList<CloudFileModel>
		get() {
			val previewCloudFiles = ArrayList<CloudFileModel>()
			view?.renderedCloudNodes()
				?.filterIsInstance<CloudFileModel>()
				?.filterTo(previewCloudFiles) {
					!it.name.endsWith(".gif") && isImageMediaType(it.name)
				}
			return previewCloudFiles
		}

	private fun shareFiles(shareFiles: List<CloudFileModel>) {
		val shareFilesIntent = Intent(Intent.ACTION_SEND_MULTIPLE)
		shareFilesIntent.type = combinedMimeType(shareFiles).toString()
		shareFilesIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, fileUtil.contentUrisFor(shareFiles))
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
		view?.showProgress(sourceFiles, ProgressModel(ProgressStateModel.MOVING))
		moveFilesUseCase //
			.withParent(targetFolder.toCloudNode()) //
			.andSourceFiles(cloudFileModelMapper.fromModels(sourceFiles)) //
			.run(object : DefaultResultHandler<List<CloudFile>>() {
				override fun onSuccess(cloudFiles: List<CloudFile>) {
					view?.deleteCloudNodesFromAdapter(sourceFiles)
				}
			})
	}

	private fun moveCloudFolder(targetFolder: CloudFolderModel, sourceFolders: List<CloudFolderModel>) {
		view?.showProgress(sourceFolders, ProgressModel(ProgressStateModel.MOVING))
		moveFoldersUseCase //
			.withParent(targetFolder.toCloudNode()) //
			.andSourceFolders(cloudFolderModelMapper.fromModels(sourceFolders)) //
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
		view?.showUploadDialog(nonReplacing.size + replacing.size)
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

	fun onExportFileClicked(cloudFile: CloudFileModel, trigger: ExportOperation) {
		exportFileToUserSelectedLocation(cloudFile, trigger)
	}

	fun onExportNodesClicked(selectedCloudFiles: ArrayList<CloudNodeModel<*>>, trigger: ExportOperation) {
		exportNodesToUserSelectedLocation(selectedCloudFiles, trigger)
	}

	private fun exportFileToUserSelectedLocation(fileToExport: CloudFileModel, exportOperation: ExportOperation) {
		val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
		intent.addCategory(Intent.CATEGORY_OPENABLE)
		intent.type = "*/*"
		intent.putExtra(Intent.EXTRA_TITLE, fileToExport.name)
		requestActivityResult(ActivityResultCallbacks.exportFileToUserSelectedLocation(fileToExport, exportOperation), intent)
	}

	private fun exportNodesToUserSelectedLocation(nodesToExport: ArrayList<CloudNodeModel<*>>, exportOperation: ExportOperation) {
		try {
			requestActivityResult( //
				ActivityResultCallbacks.pickedLocalStorageLocationForBrowsingFiles(nodesToExport, exportOperation),  //
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
		nodesToExport: ArrayList<CloudNodeModel<*>>,  //
		exportOperation: ExportOperation
	) {
		val pickedLocalStorageLocation = DocumentsContract.buildChildDocumentsUriUsingTree( //
			Uri.parse(result.intent().data.toString()),  //
			DocumentsContract.getTreeDocumentId( //
				Uri.parse(result.intent().data.toString())
			)
		)
		collectNodesToExport(
			pickedLocalStorageLocation,  //
			exportOperation,  //
			nodesToExport
		)
		disableSelectionMode()
	}

	private fun collectNodesToExport(
		parentUri: Uri,  //
		exportOperation: ExportOperation,  //
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
		collectFolderContentForExport(parentUri, exportOperation, foldersForRecursiveDirListing, filesToExport)
	}

	private fun collectFolderContentForExport(
		parentUri: Uri, exportOperation: ExportOperation, folders: List<CloudFolderModel>,  //
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
					prepareExportingOf(parentUri, exportOperation, filesToExport, cloudNodeRecursiveListing)
				}
			})
	}

	private fun prepareExportingOf(parentUri: Uri, exportOperation: ExportOperation, filesToExport: List<CloudFileModel>, cloudNodeRecursiveListing: CloudNodeRecursiveListing) {
		downloadFiles = ArrayList()
		downloadFiles.addAll(prepareFilesForExport(cloudFileModelMapper.fromModels(filesToExport), parentUri))
		cloudNodeRecursiveListing.foldersContent.forEach { folderRecursiveListing ->
			prepareFolderContentForExport(folderRecursiveListing, parentUri)
		}
		if (downloadFiles.isEmpty()) {
			view?.showMessage(R.string.screen_file_browser_nothing_to_export)
			view?.closeDialog()
		} else {
			exportOperation.export(this, downloadFiles)
		}
	}

	private fun prepareFilesForExport(filesToExport: List<CloudFile>, parentUri: Uri): List<DownloadFile> {
		return filesToExport.mapTo(ArrayList()) { createDownloadFile(it, parentUri) }
	}

	private fun prepareFolderContentForExport(cloudFolderRecursiveListing: CloudFolderRecursiveListing, parentUri: Uri) {
		createFolder(parentUri, cloudFolderRecursiveListing.parent.name)?.let {
			downloadFiles.addAll(prepareFilesForExport(cloudFolderRecursiveListing.files, it))
			cloudFolderRecursiveListing.folders.forEach { childFolder ->
				prepareFolderContentForExport(childFolder, it)
			}
		} ?: throw FatalBackendException("Failed to create parent folder for export")
	}

	private fun createFolder(parentUri: Uri, folderName: String): Uri? {
		return try {
			DocumentsContract.createDocument( //
				context().contentResolver,  //
				parentUri,  //
				DocumentsContract.Document.MIME_TYPE_DIR,  //
				folderName
			)
		} catch (e: FileNotFoundException) {
			Timber.tag("BrowseFilesPresenter").e(e)
			throw IllegalStateException("Creating folder failed")
		}
	}

	private fun createDownloadFile(file: CloudFile, documentUri: Uri): DownloadFile {
		return try {
			DownloadFile.Builder() //
				.setDownloadFile(file) //
				.setDataSink(
					contentResolverUtil.openOutputStream( //
						createNewDocumentUri(documentUri, file.name)
					)
				) //
				.build()
		} catch (e: FileNotFoundException) {
			showError(e)
			disableSelectionMode()
			throw FatalBackendException(e)
		} catch (e: NoSuchCloudFileException) {
			showError(e)
			disableSelectionMode()
			throw FatalBackendException(e)
		} catch (e: IllegalFileNameException) {
			showError(e)
			disableSelectionMode()
			throw FatalBackendException(e)
		}
	}

	@Throws(IllegalFileNameException::class, NoSuchCloudFileException::class)
	private fun createNewDocumentUri(parentUri: Uri, fileName: String): Uri {
		val mimeType = mimeTypes.fromFilename(fileName) ?: MimeType.APPLICATION_OCTET_STREAM
		return try {
			DocumentsContract.createDocument( //
				context().contentResolver,  //
				parentUri,  //
				mimeType.toString(),  //
				fileName
			)
		} catch (e: FileNotFoundException) {
			throw NoSuchCloudFileException(fileName)
		} ?: throw IllegalFileNameException()
	}

	@Callback
	fun exportFileToUserSelectedLocation(result: ActivityResult, fileToExport: CloudFileModel, exportOperation: ExportOperation) {
		try {
			val downloadFile = DownloadFile.Builder() //
				.setDownloadFile(fileToExport.toCloudNode()) //
				.setDataSink(contentResolverUtil.openOutputStream(Uri.parse(result.intent().dataString))) //
				.build()
			exportOperation.export(this, listOf(downloadFile))
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
		val decryptData = downloadFileUtil.createDecryptedDataFor(this, textFile)
		downloadFilesUseCase //
			.withDownloadFiles( //
				listOf(
					DownloadFile.Builder() //
						.setDownloadFile(textFile.toCloudNode()) //
						.setDataSink(decryptData) //
						.build()
				)
			) //
			.run(object : DefaultProgressAwareResultHandler<List<CloudFile>, DownloadState>() {
				override fun onProgress(progress: Progress<DownloadState>) {
					if (!newlyCreated) {
						view?.showProgress(textFile, progressModelMapper.toModel(progress))
					}
				}

				override fun onSuccess(files: List<CloudFile>) {
					if (!newlyCreated) {
						view?.hideProgress(textFile)
					}
					if (internalEditor) {
						startIntent(
							Intents.textEditorIntent() //
								.withTextFile(textFile)
						)
					} else {
						viewExternalFile(textFile)
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
		if(!resumedAfterAuthentication) {
			getCloudList(folder)
		}
	}

	fun onExportFolderClicked(cloudFolder: CloudFolderModel, exportTriggeredByUser: ExportOperation) {
		val nodes = ArrayList<CloudNodeModel<*>>()
		nodes.add(cloudFolder)
		onExportNodesClicked(nodes, exportTriggeredByUser)
	}

	fun exportNodesCanceled() {
		downloadFilesUseCase.unsubscribe()
		view?.closeDialog()
	}

	fun invalidateOptionsMenu() {
		activity().invalidateOptionsMenu()
	}

	fun openFileFinished() {
		try {
			// necessary see https://community.cryptomator.org/t/android-tabelle-nach-upload-unlesbar/6550
			Thread.sleep(500)
		} catch (e: InterruptedException) {
			Timber.tag("BrowseFilesPresenter").e(e, "Failed to sleep after resuming editing, necessary for google office apps")
		}
		if (sharedPreferencesHandler.keepUnlockedWhileEditing()) {
			val cryptomatorApp = activity().application as CryptomatorApp
			cryptomatorApp.unSuspendLock()
		}
		hideWritableNotification()

		context().revokeUriPermission(uriToOpenedFile, Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)

		uriToOpenedFile?.let {
			try {
				calculateDigestFromUri(it)?.let { hashAfterEdit ->
					openedCloudFileMd5?.let { hashBeforeEdit ->
						if (hashAfterEdit.contentEquals(hashBeforeEdit)) {
							Timber.tag("BrowseFilesPresenter").i("Opened app finished, file not changed")
						} else {
							uploadChangedFile()
						}
					}
				}
			} catch (e: FileNotFoundException) {
				Timber.tag("BrowseFilesPresenter").e(e, "Failed to read back changes, file isn't present anymore")
				Toast.makeText(context(), R.string.error_file_not_found_after_opening_using_3party, Toast.LENGTH_LONG).show()
			}
		}
	}

	private fun uploadChangedFile() {
		view?.showUploadDialog(1)
		openedCloudFile?.let { openedCloudFile ->
			openedCloudFile.parent?.let { openedCloudFilesParent ->
				uriToOpenedFile?.let { uriToOpenedFile ->
					uploadFilesUseCase //
						.withParent(openedCloudFilesParent.toCloudNode()) //
						.andFiles(listOf(createUploadFile(openedCloudFile.name, uriToOpenedFile, true))) //
						.run(object : DefaultProgressAwareResultHandler<List<CloudFile>, UploadState>() {
							override fun onProgress(progress: Progress<UploadState>) {
								view?.showProgress(progressModelMapper.toModel(progress))
							}

							override fun onSuccess(files: List<CloudFile>) {
								files.forEach { file ->
									view?.addOrUpdateCloudNode(cloudFileModelMapper.toModel(file))
								}
								onFileUploadCompleted()
							}

							override fun onError(e: Throwable) {
								onFileUploadError()
								if (ExceptionUtil.contains(e, CloudNodeAlreadyExistsException::class.java)) {
									ExceptionUtil.extract(e, CloudNodeAlreadyExistsException::class.java).get().message?.let {
										onCloudNodeAlreadyExists(it)
									} ?: super.onError(e)
								} else {
									super.onError(e)
								}
							}
						})
				}
			}
		}
	}

	private fun hideWritableNotification() {
		// openWritableFileNotification can not be made serializable because of this, can be null after Activity resumed
		openWritableFileNotification?.hide() ?: OpenWritableFileNotification(context(), Uri.EMPTY).hide()
	}

	@Throws(FileNotFoundException::class)
	private fun calculateDigestFromUri(uri: Uri): ByteArray? {
		val digest = MessageDigest.getInstance("MD5")
		DigestInputStream(context().contentResolver.openInputStream(uri), digest).use { dis ->
			val buffer = ByteArray(8192)
			// Read all bytes:
			while (dis.read(buffer) > -1) {
			}
		}
		return digest.digest()
	}

	interface ExportOperation : Serializable {

		fun export(presenter: BrowseFilesPresenter, downloadFiles: List<DownloadFile>)

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

		val EXPORT_AFTER_APP_CHOOSER: ExportOperation = object : ExportOperation {
			override fun export(presenter: BrowseFilesPresenter, downloadFiles: List<DownloadFile>) {
				presenter.copyFile(downloadFiles)
			}
		}
		val EXPORT_TRIGGERED_BY_USER: ExportOperation = object : ExportOperation {
			override fun export(presenter: BrowseFilesPresenter, downloadFiles: List<DownloadFile>) {
				presenter.exportFile(downloadFiles)
			}
		}
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
			copyDataUseCase,  //
			moveFilesUseCase,  //
			moveFoldersUseCase, //
			getDecryptedCloudForVaultUseCase
		)
		this.authenticationExceptionHandler = authenticationExceptionHandler
	}
}
