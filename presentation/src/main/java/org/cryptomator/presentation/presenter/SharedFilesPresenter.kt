package org.cryptomator.presentation.presenter

import android.net.Uri
import org.cryptomator.data.cloud.crypto.CryptoCloud
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.Vault
import org.cryptomator.domain.di.PerView
import org.cryptomator.domain.usecases.GetDecryptedCloudForVaultUseCase
import org.cryptomator.domain.usecases.cloud.GetCloudListUseCase
import org.cryptomator.domain.usecases.cloud.GetRootFolderUseCase
import org.cryptomator.domain.usecases.cloud.Progress
import org.cryptomator.domain.usecases.cloud.UploadFile
import org.cryptomator.domain.usecases.cloud.UploadFilesUseCase
import org.cryptomator.domain.usecases.cloud.UploadState
import org.cryptomator.domain.usecases.vault.GetVaultListUseCase
import org.cryptomator.generator.Callback
import org.cryptomator.generator.InstanceState
import org.cryptomator.presentation.R
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.intent.ChooseCloudNodeSettings
import org.cryptomator.presentation.intent.Intents
import org.cryptomator.presentation.intent.UnlockVaultIntent
import org.cryptomator.presentation.model.CloudFolderModel
import org.cryptomator.presentation.model.CloudModel
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.SharedFileModel
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.presentation.model.mappers.CloudFolderModelMapper
import org.cryptomator.presentation.model.mappers.ProgressModelMapper
import org.cryptomator.presentation.ui.activity.view.SharedFilesView
import org.cryptomator.presentation.util.ContentResolverUtil
import org.cryptomator.presentation.util.FileNameValidator.Companion.isInvalidName
import org.cryptomator.presentation.workflow.ActivityResult
import org.cryptomator.presentation.workflow.AuthenticationExceptionHandler
import org.cryptomator.util.file.FileCacheUtils
import javax.inject.Inject
import timber.log.Timber

@PerView
class SharedFilesPresenter @Inject constructor( //
	private val getVaultListUseCase: GetVaultListUseCase,  //
	private val getRootFolderUseCase: GetRootFolderUseCase,  //
	private val getDecryptedCloudForVaultUseCase: GetDecryptedCloudForVaultUseCase,  //
	private val uploadFilesUseCase: UploadFilesUseCase,  //
	private val getCloudListUseCase: GetCloudListUseCase,  //
	private val contentResolverUtil: ContentResolverUtil,  //
	private val fileCacheUtils: FileCacheUtils,  //
	private val authenticationExceptionHandler: AuthenticationExceptionHandler,  //
	private val cloudFolderModelMapper: CloudFolderModelMapper,  //
	private val progressModelMapper: ProgressModelMapper,  //
	exceptionMappings: ExceptionHandlers
) : Presenter<SharedFilesView>(exceptionMappings) {

	private val filesForUpload: MutableSet<UploadFile> = HashSet()
	private val existingFilesForUpload: MutableSet<UploadFile> = HashSet()

	@JvmField
	@InstanceState
	var selectedVault: VaultModel? = null

	@JvmField
	@InstanceState
	var location: CloudFolderModel? = null
	private var hasFileNameConflict = false
	private var authenticationState: AuthenticationState? = null
	private var tmpTextFileUri: Uri? = null

	fun onFileShared(uri: Uri) {
		if (contentResolverUtil.isFileUriPointingToFolder(uri)) {
			Timber.tag("SharedFile").i("Received 1 folder")
			collectFolderContent(uri)
		} else {
			Timber.tag("SharedFile").i("Received 1 file")
			contentResolverUtil.fileName(uri)
				?.let { filesForUpload.add(createUploadFile(it, uri)) }
				?: Timber.tag("SharedFile").i("The file doesn't have a path in the URI")
		}
	}

	fun onFilesShared(uris: List<Uri>) {
		Timber.tag("SharedFile").i("Received %d files", uris.size)
		uris.forEach { uri ->
			contentResolverUtil.fileName(uri)
				?.let { filesForUpload.add(createUploadFile(it, uri)) }
				?: Timber.tag("SharedFile").i("The file doesn't have a path in the URI")
		}
	}

	fun onTextShared(text: String?) {
		tmpTextFileUri = fileCacheUtils.tmpFile().withContent(text).create()
		val fileName = context().getString(R.string.screen_share_files_new_text_file)
		tmpTextFileUri?.let { filesForUpload.add(createUploadFile(fileName, it)) }
		Timber.tag("SharedText").i("Received text")
	}

	fun initialize() {
		displayFilesToUpload()
		displayVaults()
	}

	private fun collectFolderContent(uri: Uri) {
		val fileUris = contentResolverUtil.collectFolderContent(uri)
		onFilesShared(fileUris)
	}

	fun displayVaults() {
		getVaultListUseCase.run(object : DefaultResultHandler<List<Vault>>() {
			override fun onSuccess(vaults: List<Vault>) {
				if (vaults.isEmpty()) {
					view?.displayDialogUnableToUploadFiles()
				} else {
					val vaultModels: MutableList<VaultModel> = vaults.mapTo(ArrayList()) { VaultModel(it) }
					view?.displayVaults(vaultModels)
				}
			}
		})
	}

	private fun displayFilesToUpload() {
		view?.displayFilesToUpload(filesForUploadAsSharedFileModels())
	}

	private fun filesForUploadAsSharedFileModels(): List<SharedFileModel> {
		return filesForUpload.mapTo(ArrayList(filesForUpload.size)) { SharedFileModel(it, it.fileName) }
	}

	private fun authenticate(vaultModel: VaultModel?, authenticationState: AuthenticationState = AuthenticationState.CHOOSE_LOCATION) {
		setAuthenticationState(authenticationState)
		vaultModel?.let { onCloudOfVaultAuthenticated(it.toVault()) }
	}

	private fun onCloudOfVaultAuthenticated(authenticatedVault: Vault) {
		if (authenticatedVault.isUnlocked) {
			decryptedCloudFor(authenticatedVault)
		} else {
			if (!isPaused) {
				requestActivityResult( //
					ActivityResultCallbacks.vaultUnlockedSharedFiles(), //
					Intents.unlockVaultIntent().withVaultModel(VaultModel(authenticatedVault)).withVaultAction(UnlockVaultIntent.VaultAction.UNLOCK)
				)
			}
		}
	}


	@Callback
	fun vaultUnlockedSharedFiles(result: ActivityResult) {
		val cloud = result.intent().getSerializableExtra(SINGLE_RESULT) as Cloud
		rootFolderFor(cloud)
	}

	private fun decryptedCloudFor(vault: Vault) {
		getDecryptedCloudForVaultUseCase //
			.withVault(vault) //
			.run(object : DefaultResultHandler<Cloud>() {
				override fun onSuccess(cloud: Cloud) {
					rootFolderFor(cloud)
				}

				override fun onError(e: Throwable) {
					if (!authenticationExceptionHandler.handleAuthenticationException(this@SharedFilesPresenter, e, ActivityResultCallbacks.decryptedCloudForAfterAuth(vault))) {
						super.onError(e)
					}
				}
			})
	}

	@Callback
	fun decryptedCloudForAfterAuth(result: ActivityResult, vault: Vault?) {
		val cloud = result.getSingleResult(CloudModel::class.java).toCloud()
		decryptedCloudFor(Vault.aCopyOf(vault).withCloud(cloud).build())
	}

	private fun rootFolderFor(cloud: Cloud) {
		getRootFolderUseCase //
			.withCloud(cloud) //
			.run(object : DefaultResultHandler<CloudFolder>() {
				override fun onSuccess(folder: CloudFolder) {
					when (authenticationState) {
						AuthenticationState.CHOOSE_LOCATION -> navigateToVaultContent((folder.cloud as CryptoCloud).vault, folder)
						AuthenticationState.INIT_ROOT -> {
							location = cloudFolderModelMapper.toModel(folder)
							checkForUsedFileNames(folder)
						}
						else -> {}
					}
				}
			})
	}

	private fun navigateToVaultContent(vault: Vault, folder: CloudFolder) {
		if (!isPaused) {
			navigateToVaultContent(VaultModel(vault), cloudFolderModelMapper.toModel(folder))
		}
	}

	private fun setLocation(location: CloudFolderModel) {
		this.location = location
	}

	private fun uploadFiles(
		nonReplacing: Set<UploadFile>,  //
		replacing: Set<UploadFile>,  //
		folder: CloudFolder
	) {
		if (nonReplacing.size + replacing.size == 0) {
			view?.finish()
		}
		view?.showUploadDialog(nonReplacing.size + replacing.size)
		val filesReadyForUpload: MutableList<UploadFile> = ArrayList()
		filesReadyForUpload.addAll(nonReplacing)
		filesReadyForUpload.addAll(replacing)
		uploadFiles(folder, filesReadyForUpload)
	}

	private fun uploadFiles(folder: CloudFolder, files: List<UploadFile>) {
		uploadFilesUseCase //
			.withParent(folder) //
			.andFiles(files) //
			.run(object : DefaultProgressAwareResultHandler<List<CloudFile>, UploadState>() {
				override fun onProgress(progress: Progress<UploadState>) {
					view?.showProgress(progressModelMapper.toModel(progress))
				}

				override fun onFinished() {
					onFileUploadCompleted()
				}
			})
	}

	private fun onFileUploadCompleted() {
		deleteTmpFileIfPresent()
		view?.showProgress(ProgressModel.COMPLETED)
		view?.showMessage(context().getString(R.string.screen_share_files_msg_success))
		view?.finish()
	}

	private fun deleteTmpFileIfPresent() {
		tmpTextFileUri?.let { fileCacheUtils.deleteTmpFile(it) }
	}

	fun onReplaceExistingFilesPressed() {
		differencesOfUploadAndExistingFiles()
		location?.let { uploadFiles(filesForUpload, existingFilesForUpload, it.toCloudNode()) }
	}

	fun onSkipExistingFilesPressed() {
		differencesOfUploadAndExistingFiles()
		location?.let { uploadFiles(filesForUpload, emptySet(), it.toCloudNode()) }
	}

	private fun differencesOfUploadAndExistingFiles() {
		filesForUpload.removeAll(existingFilesForUpload)
	}

	private fun checkForUsedFileNames(folder: CloudFolder) {
		view?.showProgress(ProgressModel.GENERIC)
		getCloudListUseCase //
			.withFolder(folder) //
			.run(object : DefaultResultHandler<List<CloudNode>>() {
				override fun onSuccess(currentCloudNodes: List<CloudNode>) {
					checkForExistingFilesOrUploadFiles(folder, currentCloudNodes)
				}
			})
	}

	private fun hasUsedFileNamesAtLocation(currentCloudNodes: List<CloudNode>): Boolean {
		existingFilesForUpload.clear()
		currentCloudNodes.forEach { cloudNode ->
			fileForUploadWithName(cloudNode.name)?.let {
				if (cloudNode is CloudFile) {
					filesForUpload.remove(it)
					existingFilesForUpload.add( //
						UploadFile.aCopyOf(it) //
							.thatIsReplacing(true) //
							.build()
					)
				} else {
					// remove file when name is used by a folder
					filesForUpload.remove(it)
				}
			}
		}
		return existingFilesForUpload.isNotEmpty()
	}

	private fun fileForUploadWithName(name: String): UploadFile? {
		return filesForUpload.firstOrNull { it.fileName == name }
	}

	private fun checkForExistingFilesOrUploadFiles(folder: CloudFolder, currentCloudNodes: List<CloudNode>) {
		if (hasUsedFileNamesAtLocation(currentCloudNodes)) {
			view?.showReplaceDialog(namesOfExistingFiles(), filesForUpload.size)
		} else {
			uploadFiles(filesForUpload, emptySet(), folder)
		}
	}

	private fun namesOfExistingFiles(): List<String> {
		return existingFilesForUpload.mapTo(ArrayList()) { it.fileName }
	}

	fun onSaveButtonPressed(filesForUpload: List<SharedFileModel>) {
		updateFileNames(filesForUpload)
		when {
			hasFileNameConflict() -> {
				view?.showMessage(R.string.screen_share_files_msg_filenames_must_be_unique)
			}
			hasInvalidFileNames(filesForUpload) -> {
				view?.showMessage(R.string.error_names_contains_invalid_characters)
			}
			else -> {
				prepareSavingFiles()
			}
		}
	}

	private fun prepareSavingFiles() {
		location?.let { checkForUsedFileNames(it.toCloudNode()) }
			?: authenticate(selectedVault, AuthenticationState.INIT_ROOT)
	}

	private fun hasInvalidFileNames(filesForUpload: List<SharedFileModel>): Boolean {
		filesForUpload.forEach { file ->
			if (isInvalidName(file.fileName)) {
				return true
			}
		}
		return false
	}

	private fun updateFileNames(sharedFiles: List<SharedFileModel>) {
		filesForUpload.clear()
		sharedFiles.mapTo(filesForUpload) {  //
			UploadFile.aCopyOf(it.id as UploadFile).withFileName(it.fileName).build()
		}
	}

	private fun navigateToVaultContent(vaultModel: VaultModel, decryptedRoot: CloudFolderModel) {
		requestActivityResult( //
			ActivityResultCallbacks.onChooseLocation(vaultModel),  //
			Intents.browseFilesIntent() //
				.withVaultId(vaultModel.vaultId) //
				.withFolder(decryptedRoot) //
				.withTitle(vaultModel.name) //
				.withChooseCloudNodeSettings( //
					ChooseCloudNodeSettings.chooseCloudNodeSettings() //
						.withExtraTitle(context().getString(R.string.screen_file_browser_share_destination_title)) //
						.withExtraToolbarIcon(R.drawable.ic_clear) //
						.withButtonText(context().getString(R.string.screen_file_browser_share_button_text)) //
						.selectingFolders() //
						.build()
				)
		)
	}

	@Callback
	fun onChooseLocation(result: ActivityResult, vaultModel: VaultModel?) {
		val folder = result.singleResult as CloudFolderModel
		setLocation(folder)
		view?.showChosenLocation(folder)
	}

	fun onChooseLocationPressed() {
		authenticate(selectedVault)
	}

	fun onFileNameConflict(hasFileNameConflict: Boolean) {
		this.hasFileNameConflict = hasFileNameConflict
		if (hasFileNameConflict()) {
			view?.showMessage(R.string.screen_share_files_msg_filenames_must_be_unique)
		}
	}

	private fun hasFileNameConflict(): Boolean {
		return hasFileNameConflict
	}

	fun onVaultSelected(vault: VaultModel?) {
		selectedVault = vault
	}

	private fun setAuthenticationState(authenticationState: AuthenticationState) {
		this.authenticationState = authenticationState
	}

	fun onUploadCanceled() {
		uploadFilesUseCase.unsubscribe()
		view?.closeDialog()
	}

	private enum class AuthenticationState {
		CHOOSE_LOCATION, INIT_ROOT
	}

	private fun createUploadFile(fileName: String, uri: Uri): UploadFile {
		return UploadFile.anUploadFile() //
			.withFileName(fileName) //
			.withDataSource(UriBasedDataSource.from(uri)) //
			.thatIsReplacing(false) //
			.build()
	}

	init {
		unsubscribeOnDestroy( //
			getRootFolderUseCase,  //
			getVaultListUseCase,  //
			getDecryptedCloudForVaultUseCase,  //
			uploadFilesUseCase,  //
			getCloudListUseCase
		)
	}
}
