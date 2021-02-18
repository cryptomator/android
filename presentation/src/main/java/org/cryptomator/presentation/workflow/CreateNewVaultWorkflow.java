package org.cryptomator.presentation.workflow;

import android.content.Context;

import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.Vault;
import org.cryptomator.domain.usecases.cloud.GetRootFolderUseCase;
import org.cryptomator.domain.usecases.vault.CreateVaultUseCase;
import org.cryptomator.generator.Callback;
import org.cryptomator.presentation.R;
import org.cryptomator.presentation.model.CloudFolderModel;
import org.cryptomator.presentation.model.CloudModel;
import org.cryptomator.presentation.model.ProgressModel;
import org.cryptomator.presentation.model.mappers.CloudModelMapper;
import org.cryptomator.presentation.presenter.VaultListPresenter;

import java.io.Serializable;

import javax.inject.Inject;

import static java.util.Collections.singletonList;
import static org.cryptomator.presentation.intent.ChooseCloudNodeSettings.chooseCloudNodeSettings;
import static org.cryptomator.presentation.intent.Intents.browseFilesIntent;
import static org.cryptomator.presentation.intent.Intents.chooseCloudServiceIntent;
import static org.cryptomator.presentation.intent.Intents.createVaultIntent;
import static org.cryptomator.presentation.intent.Intents.setPasswordIntent;

public class CreateNewVaultWorkflow extends Workflow<CreateNewVaultWorkflow.State> {

	private final CreateVaultUseCase createVaultUseCase;
	private final GetRootFolderUseCase getRootFolderUseCase;
	private final CloudModelMapper cloudModelMapper;
	private final AuthenticationExceptionHandler authenticationExceptionHandler;
	private final Context context;

	@Inject
	public CreateNewVaultWorkflow( //
			Context context, //
			CreateVaultUseCase createVaultUseCase, //
			GetRootFolderUseCase getRootFolderUseCase, //
			CloudModelMapper cloudModelMapper, //
			AuthenticationExceptionHandler authenticationExceptionHandler) {
		super(new State());
		this.context = context;
		this.createVaultUseCase = createVaultUseCase;
		this.getRootFolderUseCase = getRootFolderUseCase;
		this.cloudModelMapper = cloudModelMapper;
		this.authenticationExceptionHandler = authenticationExceptionHandler;
	}

	@Override
	void doStart() {
		String subtitle = presenter().context().getString(R.string.screen_choose_cloud_service_subtitle_create_new_vault);
		chain(chooseCloudServiceIntent().withSubtitle(subtitle), SerializableResultCallbacks.onCloudServiceChosen());
	}

	@Callback
	void onCloudServiceChosen(SerializableResult<CloudModel> result) {
		onCloudServiceChosen(result.getResult());
	}

	private void onCloudServiceChosen(CloudModel cloud) {
		presenter().getView().showProgress(ProgressModel.GENERIC);
		getRootFolderUseCase //
				.withCloud(cloud.toCloud()) //
				.run(presenter().new ProgressCompletingResultHandler<CloudFolder>() {
					@Override
					public void onSuccess(CloudFolder cloudFolder) {
						state().cloudRoot = cloudFolder;
						chain(createVaultIntent(), SerializableResultCallbacks.nameEntered());
					}

					@Override
					public void onError(Throwable e) {
						if (!authenticationExceptionHandler.handleAuthenticationException( //
								presenter(), //
								e, //
								ActivityResultCallbacks.onCloudServiceAuthenticated())) {
							super.onError(e);
						}
					}
				});
	}

	@Callback
	void onCloudServiceAuthenticated(ActivityResult result) {
		onCloudServiceChosen(result.getSingleResult(CloudModel.class));
	}

	@Callback
	void nameEntered(SerializableResult<String> result) {
		state().name = result.getResult();
		chain(browseFilesIntent() //
						.withTitle(context.getString(cloudModelMapper.toModel(state().cloudRoot.getCloud()).name())) //
						.withFolder(new CloudFolderModel(state().cloudRoot)) //
						.withChooseCloudNodeSettings( //
								chooseCloudNodeSettings() //
										.withExtraTitle(presenter().context().getString(R.string.screen_file_browser_subtitle_create_new_vault)) //
										.withExtraText(presenter().context().getString(R.string.screen_file_browser_create_new_vault_extra_text, state().name)) //
										.withButtonText(presenter().context().getString(R.string.screen_file_browser_create_new_vault_button_text)) //
										.selectingFoldersNotContaining(singletonList(state().name)) //
										.build()), //
				SerializableResultCallbacks.locationChosen());
	}

	@Callback
	void locationChosen(SerializableResult<CloudFolderModel> result) {
		state().location = result.getResult().toCloudNode();
		chain(setPasswordIntent(), SerializableResultCallbacks.passwordEntered());
	}

	@Callback
	void passwordEntered(SerializableResult<String> result) {
		state().password = result.getResult();
		finish();
	}

	@Override
	void completed() {
		presenter().getView().showProgress(ProgressModel.GENERIC);
		createVaultUseCase //
				.withVaultName(state().name) //
				.andPassword(state().password) //
				.andFolder(state().location) //
				.run(presenter().new ProgressCompletingResultHandler<Vault>() {
					@Override
					public void onSuccess(Vault vault) {
						((VaultListPresenter) presenter()).onAddOrCreateVaultCompleted(vault);
					}
				});
	}

	static class State implements Serializable {

		CloudFolder cloudRoot;

		String name;
		String password;
		CloudFolder location;

	}

}
