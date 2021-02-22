package org.cryptomator.presentation.workflow;

import android.content.Context;

import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.Vault;
import org.cryptomator.domain.di.PerView;
import org.cryptomator.domain.usecases.cloud.GetRootFolderUseCase;
import org.cryptomator.domain.usecases.vault.GetVaultListUseCase;
import org.cryptomator.domain.usecases.vault.SaveVaultUseCase;
import org.cryptomator.generator.Callback;
import org.cryptomator.presentation.R;
import org.cryptomator.presentation.model.CloudFileModel;
import org.cryptomator.presentation.model.CloudFolderModel;
import org.cryptomator.presentation.model.CloudModel;
import org.cryptomator.presentation.model.ProgressModel;
import org.cryptomator.presentation.model.mappers.CloudModelMapper;
import org.cryptomator.presentation.presenter.VaultListPresenter;

import java.io.Serializable;
import java.util.List;

import javax.inject.Inject;

import static org.cryptomator.domain.Vault.aVault;
import static org.cryptomator.presentation.intent.ChooseCloudNodeSettings.chooseCloudNodeSettings;
import static org.cryptomator.presentation.intent.Intents.browseFilesIntent;
import static org.cryptomator.presentation.intent.Intents.chooseCloudServiceIntent;

@PerView
public class AddExistingVaultWorkflow extends Workflow<AddExistingVaultWorkflow.State> {

	private final SaveVaultUseCase saveVaultUseCase;
	private final GetVaultListUseCase getVaultListUseCase;
	private final GetRootFolderUseCase getRootFolderUseCase;
	private final CloudModelMapper cloudModelMapper;
	private final AuthenticationExceptionHandler authenticationExceptionHandler;
	private final Context context;

	@Inject
	public AddExistingVaultWorkflow( //
			Context context, //
			SaveVaultUseCase saveVaultUseCase, //
			GetVaultListUseCase getVaultListUseCase, //
			GetRootFolderUseCase getRootFolderUseCase, //
			CloudModelMapper cloudModelMapper, //
			AuthenticationExceptionHandler authenticationExceptionHandler) {
		super(new State());
		this.context = context;
		this.saveVaultUseCase = saveVaultUseCase;
		this.getVaultListUseCase = getVaultListUseCase;
		this.getRootFolderUseCase = getRootFolderUseCase;
		this.cloudModelMapper = cloudModelMapper;
		this.authenticationExceptionHandler = authenticationExceptionHandler;
	}

	protected void doStart() {
		String subtitle = presenter() //
				.context() //
				.getString(R.string.screen_choose_cloud_service_subtitle_add_existing_vault);

		chain( //
				chooseCloudServiceIntent().withSubtitle(subtitle), //
				SerializableResultCallbacks.cloudServiceChosen());
	}

	@Callback
	void cloudServiceChosen(SerializableResult<CloudModel> result) {
		cloudServiceChosen(result.getResult());
	}

	private void cloudServiceChosen(CloudModel cloud) {
		presenter().getView().showProgress(ProgressModel.GENERIC);
		getRootFolderUseCase //
				.withCloud(cloud.toCloud()) //
				.run(presenter().new ProgressCompletingResultHandler<CloudFolder>() {
					@Override
					public void onSuccess(CloudFolder cloudFolder) {
						state().cloudRoot = cloudFolder;
						chain(browseFilesIntent() //
								.withTitle(context.getString(cloudModelMapper.toModel(cloudFolder.getCloud()).name())) //
								.withFolder(new CloudFolderModel(cloudFolder)) //
								.withChooseCloudNodeSettings( //
										chooseCloudNodeSettings() //
												.withExtraTitle(presenter() //
														.context() //
														.getString(R.string.screen_file_browser_subtitle_add_existing_vault)) //
												.withExtraText(presenter() //
														.context() //
														.getString(R.string.screen_file_browser_add_existing_vault_extra_text)) //
												.selectingFilesWithNameOnly("masterkey.cryptomator") //
												.build()), //
								SerializableResultCallbacks.masterkeyFileChosen());
					}

					@Override
					public void onError(Throwable e) {
						if (!authenticationExceptionHandler.handleAuthenticationException( //
								presenter(), //
								e, //
								ActivityResultCallbacks.cloudServiceAuthenticated())) {
							super.onError(e);
						}
					}
				});
	}

	@Callback
	void cloudServiceAuthenticated(ActivityResult result) {
		cloudServiceChosen(result.getSingleResult(CloudModel.class));
	}

	@Callback
	void masterkeyFileChosen(SerializableResult<CloudFileModel> result) {
		CloudFileModel masterkeyFile = result.getResult();
		state().masterkeyFile = masterkeyFile.toCloudNode();
		presenter().getView().showProgress(ProgressModel.GENERIC);
		finish();
	}

	@Override
	void completed() {
		presenter().getView().showProgress(ProgressModel.GENERIC);
		getVaultListUseCase.run(presenter().new ProgressCompletingResultHandler<List<Vault>>() {
			@Override
			public void onSuccess(List<Vault> vaults) {
				saveVaultUseCase//
						.withVault(aVault() //
								.withNamePathAndCloudFrom(state().masterkeyFile.getParent()) //
								.withPosition(vaults.size()) //
								.thatIsNew() //
								.build()) //
						.run(presenter().new ProgressCompletingResultHandler<Vault>() {
							@Override
							public void onSuccess(Vault vault) {
								((VaultListPresenter) presenter()).onAddOrCreateVaultCompleted(vault);
							}
						});
			}
		});
	}

	public static class State implements Serializable {

		CloudFolder cloudRoot;
		CloudFile masterkeyFile;

	}

}
