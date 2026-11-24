package org.cryptomator.presentation.presenter;

import android.app.Activity;
import android.content.Intent;

import org.cryptomator.data.cloud.crypto.CryptoCloud;
import org.cryptomator.data.util.NetworkConnectionCheck;
import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.CloudType;
import org.cryptomator.domain.OnedriveCloud;
import org.cryptomator.domain.Vault;
import org.cryptomator.domain.usecases.DoUpdateCheckUseCase;
import org.cryptomator.domain.usecases.DoUpdateUseCase;
import org.cryptomator.domain.usecases.GetDecryptedCloudForVaultUseCase;
import org.cryptomator.domain.usecases.ResultHandler;
import org.cryptomator.domain.usecases.cloud.GetRootFolderUseCase;
import org.cryptomator.domain.usecases.vault.CreatedVault;
import org.cryptomator.domain.usecases.vault.DeleteVaultUseCase;
import org.cryptomator.domain.usecases.vault.GetVaultListUseCase;
import org.cryptomator.domain.usecases.vault.ListCBCEncryptedPasswordVaultsUseCase;
import org.cryptomator.domain.usecases.vault.LockVaultUseCase;
import org.cryptomator.domain.usecases.vault.MoveVaultPositionUseCase;
import org.cryptomator.domain.usecases.vault.RemoveStoredVaultPasswordsUseCase;
import org.cryptomator.domain.usecases.vault.RenameVaultUseCase;
import org.cryptomator.domain.usecases.vault.SaveVaultUseCase;
import org.cryptomator.domain.usecases.vault.SaveVaultsUseCase;
import org.cryptomator.domain.usecases.vault.UnlockToken;
import org.cryptomator.presentation.exception.ExceptionHandlers;
import org.cryptomator.presentation.licensing.LicenseEnforcer;
import org.cryptomator.presentation.model.VaultModel;
import org.cryptomator.presentation.model.mappers.CloudFolderModelMapper;
import org.cryptomator.presentation.service.ProductPricesCache;
import org.cryptomator.presentation.ui.activity.view.VaultListView;
import org.cryptomator.presentation.util.FileUtil;
import org.cryptomator.presentation.workflow.ActivityResult;
import org.cryptomator.presentation.workflow.AddExistingVaultWorkflow;
import org.cryptomator.presentation.workflow.AuthenticationExceptionHandler;
import org.cryptomator.presentation.workflow.CreateNewVaultWorkflow;
import org.cryptomator.util.FlavorConfig;
import org.cryptomator.util.SharedPreferencesHandler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;

import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static java.util.Arrays.asList;

public class VaultListPresenterTest {

	private static final String A_NEW_VAULT_NAME = "Haribo";

	private static final String A_RECOVERY_KEY = "pathway lift abuse";

	private static final Cloud A_CLOUD = OnedriveCloud //
			.aOnedriveCloud() //
			.build();

	private static final Vault AN_UNLOCKED_VAULT = Vault.aVault() //
			.withId(1L) //
			.withPosition(1) //
			.withName("Top Secret") //
			.withPath("/top secret") //
			.withCloudType(CloudType.DROPBOX) //
			.withUnlocked(true).build();

	private static final Vault ANOTHER_VAULT_WITH_CLOUD = Vault.aVault() //
			.withId(2L) //
			.withPosition(2) //
			.withName("Trip to the moon") //
			.withPath("/trip to the moon") //
			.withCloudType(CloudType.ONEDRIVE) //
			.withCloud(A_CLOUD).build();

	private static final Vault A_VAULT_WITH_NEW_NAME = Vault.aVault() //
			.withId(3L) //
			.withPosition(3) //
			.withName(A_NEW_VAULT_NAME) //
			.withPath("/trip to the moon") //
			.withCloudType(CloudType.GOOGLE_DRIVE) //
			.withUnlocked(false) //
			.build();

	private static final VaultModel AN_UNLOCKED_VAULT_MODEL = new VaultModel(AN_UNLOCKED_VAULT);

	private static final VaultModel ANOTHER_VAULT_MODEL_WITH_CLOUD = new VaultModel(ANOTHER_VAULT_WITH_CLOUD);

	private static final VaultModel A_VAULT_MODEL_WITH_NEW_NAME = new VaultModel(A_VAULT_WITH_NEW_NAME);

	private static final Throwable AN_EXCEPTION = new Exception();
	private final CloudFolderModelMapper cloudNodeModelMapper = Mockito.mock(CloudFolderModelMapper.class);
	public Activity activity = Mockito.mock(Activity.class);
	private VaultListView vaultListView = Mockito.mock(VaultListView.class);
	private GetVaultListUseCase getVaultListUseCase = Mockito.mock(GetVaultListUseCase.class);
	private DeleteVaultUseCase deleteVaultUseCase = Mockito.mock(DeleteVaultUseCase.class);
	private DeleteVaultUseCase.Launcher deleteVaultUseCaseLauncher = Mockito.mock(DeleteVaultUseCase.Launcher.class);
	private RenameVaultUseCase renameVaultUseCase = Mockito.mock(RenameVaultUseCase.class);
	private RenameVaultUseCase.Launcher renameVaultUseCaseLauncher = Mockito.mock(RenameVaultUseCase.Launcher.class);
	private LockVaultUseCase lockVaultUseCase = Mockito.mock(LockVaultUseCase.class);
	private LockVaultUseCase.Launcher lockVaultUseCaseLauncher = Mockito.mock(LockVaultUseCase.Launcher.class);
	private GetDecryptedCloudForVaultUseCase getDecryptedCloudForVaultUseCase = Mockito.mock(GetDecryptedCloudForVaultUseCase.class);
	private UnlockToken unlockToken = Mockito.mock(UnlockToken.class);
	private GetRootFolderUseCase getRootFolderUseCase = Mockito.mock(GetRootFolderUseCase.class);
	private AddExistingVaultWorkflow addExistingVaultWorkflow = Mockito.mock(AddExistingVaultWorkflow.class);
	private CreateNewVaultWorkflow createNewVaultWorkflow = Mockito.mock(CreateNewVaultWorkflow.class);
	private SaveVaultUseCase saveVaultUseCase = Mockito.mock(SaveVaultUseCase.class);
	private MoveVaultPositionUseCase moveVaultPositionUseCase = Mockito.mock(MoveVaultPositionUseCase.class);
	private DoUpdateCheckUseCase updateCheckUseCase = Mockito.mock(DoUpdateCheckUseCase.class);
	private DoUpdateUseCase updateUseCase = Mockito.mock(DoUpdateUseCase.class);
	private ListCBCEncryptedPasswordVaultsUseCase listCBCEncryptedPasswordVaultsUseCase = Mockito.mock(ListCBCEncryptedPasswordVaultsUseCase.class);
	private RemoveStoredVaultPasswordsUseCase removeStoredVaultPasswordsUseCase = Mockito.mock(RemoveStoredVaultPasswordsUseCase.class);
	private SaveVaultsUseCase saveVaultsUseCase = Mockito.mock(SaveVaultsUseCase.class);
	private NetworkConnectionCheck networkConnectionCheck = Mockito.mock(NetworkConnectionCheck.class);
	private FileUtil fileUtil = Mockito.mock(FileUtil.class);
	private AuthenticationExceptionHandler authenticationExceptionHandler = Mockito.mock(AuthenticationExceptionHandler.class);
	private LicenseEnforcer licenseEnforcer = Mockito.mock(LicenseEnforcer.class);
	private SharedPreferencesHandler sharedPreferencesHandler = Mockito.mock(SharedPreferencesHandler.class);
	private ProductPricesCache productPricesCache = Mockito.mock(ProductPricesCache.class);
	private ExceptionHandlers exceptionMappings = Mockito.mock(ExceptionHandlers.class);
	private Function0<Unit> launchReviewFlow = Mockito.mock(Function0.class);
	private VaultListPresenter inTest;

	@BeforeEach
	public void setup() {
		inTest = new VaultListPresenter(getVaultListUseCase, //
				deleteVaultUseCase, //
				renameVaultUseCase, //
				lockVaultUseCase, //
				getDecryptedCloudForVaultUseCase, //
				getRootFolderUseCase, //
				addExistingVaultWorkflow, //
				createNewVaultWorkflow, //
				saveVaultUseCase, //
				moveVaultPositionUseCase, //
				updateCheckUseCase, //
				updateUseCase, //
				listCBCEncryptedPasswordVaultsUseCase, //
				removeStoredVaultPasswordsUseCase, //
				saveVaultsUseCase, //
				networkConnectionCheck, //
				fileUtil, //
				authenticationExceptionHandler, //
				cloudNodeModelMapper, //
				licenseEnforcer, //
				sharedPreferencesHandler, //
				productPricesCache, //
				exceptionMappings);
		when(vaultListView.activity()).thenReturn(activity);
		inTest.setView(vaultListView);
	}

	@Test
	public void testCreatedVaultIsAddedAndShowsItsRecoveryKey() {
		inTest.onCreateVaultCompleted(new CreatedVault(AN_UNLOCKED_VAULT, A_RECOVERY_KEY));

		ArgumentCaptor<VaultModel> shownFor = ArgumentCaptor.forClass(VaultModel.class);
		verify(vaultListView).addOrUpdateVault(Mockito.any());
		verify(vaultListView).showRecoveryKeyDialog(shownFor.capture(), Mockito.eq(A_RECOVERY_KEY));
		Assertions.assertEquals(AN_UNLOCKED_VAULT.getName(), shownFor.getValue().getName());
	}

	@Test
	public void testVaultCreatedWhilePausedShowsItsRecoveryKeyOnResume() {
		when(sharedPreferencesHandler.hasCompletedWelcomeFlow()).thenReturn(true);
		when(sharedPreferencesHandler.licenseToken()).thenReturn("token");
		when(licenseEnforcer.evaluateTrialState()).thenReturn(new LicenseEnforcer.TrialState(false, false, null));

		inTest.pause();
		inTest.onCreateVaultCompleted(new CreatedVault(AN_UNLOCKED_VAULT, A_RECOVERY_KEY));

		verify(vaultListView).addOrUpdateVault(Mockito.any());
		verify(vaultListView, never()).showRecoveryKeyDialog(Mockito.any(), Mockito.any());

		inTest.resume();
		inTest.pause();
		inTest.resume();

		verify(vaultListView, times(1)).showRecoveryKeyDialog(Mockito.any(), Mockito.eq(A_RECOVERY_KEY));
	}

	@Test
	public void testResumedWithPendingReviewRequestRequestsReviewAndClearsIt() {
		stubResumable();
		when(sharedPreferencesHandler.reviewRequestPending()).thenReturn(true);

		inTest.resumed();

		verify(sharedPreferencesHandler).setReviewRequestPending(false);
		verify(vaultListView).requestReview();
	}

	@Test
	public void testResumedWithoutPendingReviewRequestDoesNotRequestReview() {
		stubResumable();

		inTest.resumed();

		verify(vaultListView, never()).requestReview();
	}

	@Test
	public void testResumedBeforeWelcomeFlowKeepsPendingReviewRequest() {
		when(sharedPreferencesHandler.hasCompletedWelcomeFlow()).thenReturn(false);
		when(sharedPreferencesHandler.reviewRequestPending()).thenReturn(true);

		inTest.resumed();

		verify(sharedPreferencesHandler, never()).setReviewRequestPending(false);
		verify(vaultListView, never()).requestReview();
	}

	@Test
	public void testPendingReviewRequestWaitsUntilStartupPromptsOfPrepareViewAreDone() {
		stubResumable();
		when(sharedPreferencesHandler.isScreenLockDialogAlreadyShown()).thenReturn(true);
		when(sharedPreferencesHandler.reviewRequestPending()).thenReturn(true);

		inTest.prepareView();
		inTest.resumed();
		inTest.resumed();

		verify(sharedPreferencesHandler, never()).setReviewRequestPending(false);
		verify(vaultListView, never()).requestReview();

		ArgumentCaptor<ResultHandler<List<Vault>>> cbcVaultsHandler = ArgumentCaptor.forClass(ResultHandler.class);
		verify(listCBCEncryptedPasswordVaultsUseCase).run(cbcVaultsHandler.capture());
		cbcVaultsHandler.getValue().onFinished();
		inTest.resumed();

		verify(sharedPreferencesHandler).setReviewRequestPending(false);
		verify(vaultListView).requestReview();
	}

	@Test
	public void testPendingReviewRequestWaitsWhileADialogIsOpen() {
		stubResumable();
		when(sharedPreferencesHandler.reviewRequestPending()).thenReturn(true);
		when(vaultListView.hasOpenDialog()).thenReturn(true);

		inTest.resumed();

		verify(sharedPreferencesHandler, never()).setReviewRequestPending(false);
		verify(vaultListView, never()).requestReview();

		when(vaultListView.hasOpenDialog()).thenReturn(false);
		inTest.resumed();

		verify(sharedPreferencesHandler).setReviewRequestPending(false);
		verify(vaultListView).requestReview();
	}

	@Test
	public void testReadyReviewFlowIsLaunchedWhenNothingBlocksIt() {
		inTest.onReviewFlowReady(launchReviewFlow);

		verify(launchReviewFlow).invoke();
		verify(sharedPreferencesHandler, never()).setReviewRequestPending(true);
	}

	@Test
	public void testReadyReviewFlowIsPendingAgainWhenADialogWasOpenedMeanwhile() {
		when(vaultListView.hasOpenDialog()).thenReturn(true);

		inTest.onReviewFlowReady(launchReviewFlow);

		verify(launchReviewFlow, never()).invoke();
		verify(sharedPreferencesHandler).setReviewRequestPending(true);
	}

	@Test
	public void testReadyReviewFlowIsPendingAgainWhenTheVaultListWasLeftMeanwhile() {
		inTest.pause();

		inTest.onReviewFlowReady(launchReviewFlow);

		verify(launchReviewFlow, never()).invoke();
		verify(sharedPreferencesHandler).setReviewRequestPending(true);
		verify(vaultListView, never()).hasOpenDialog();
	}

	@Test
	public void testUnlockFromVaultListIsCountedInPremiumFlavor() {
		assumeTrue(FlavorConfig.INSTANCE.isPremiumFlavor());
		when(sharedPreferencesHandler.vaultListUnlockCount()).thenReturn(0);

		unlockFromVaultList();

		verify(sharedPreferencesHandler).setVaultListUnlockCount(1);
		verify(sharedPreferencesHandler, never()).setReviewRequestPending(true);
	}

	@Test
	public void testThirdUnlockFromVaultListMarksReviewRequestPendingWithoutRequestingIt() {
		assumeTrue(FlavorConfig.INSTANCE.isPremiumFlavor());
		when(sharedPreferencesHandler.vaultListUnlockCount()).thenReturn(2);

		unlockFromVaultList();

		verify(sharedPreferencesHandler).setVaultListUnlockCount(3);
		verify(sharedPreferencesHandler).setReviewRequestPending(true);
		verify(vaultListView, never()).requestReview();
	}

	@Test
	public void testUnlocksAfterTheThirdDoNotMarkReviewRequestPendingAgain() {
		assumeTrue(FlavorConfig.INSTANCE.isPremiumFlavor());
		when(sharedPreferencesHandler.vaultListUnlockCount()).thenReturn(3);

		unlockFromVaultList();

		verify(sharedPreferencesHandler, never()).setVaultListUnlockCount(Mockito.anyInt());
		verify(sharedPreferencesHandler, never()).setReviewRequestPending(true);
	}

	@Test
	public void testUnlockFromVaultListIsNotCountedOutsidePremiumFlavor() {
		assumeFalse(FlavorConfig.INSTANCE.isPremiumFlavor());
		when(sharedPreferencesHandler.vaultListUnlockCount()).thenReturn(2);

		unlockFromVaultList();

		verify(sharedPreferencesHandler, never()).setVaultListUnlockCount(Mockito.anyInt());
		verify(sharedPreferencesHandler, never()).setReviewRequestPending(true);
	}

	private void stubResumable() {
		when(sharedPreferencesHandler.hasCompletedWelcomeFlow()).thenReturn(true);
		when(sharedPreferencesHandler.licenseToken()).thenReturn("token");
		when(licenseEnforcer.evaluateTrialState()).thenReturn(new LicenseEnforcer.TrialState(false, false, null));
	}

	private void unlockFromVaultList() {
		CryptoCloud cloud = Mockito.mock(CryptoCloud.class);
		when(cloud.getVault()).thenReturn(AN_UNLOCKED_VAULT);
		CloudFolder folder = Mockito.mock(CloudFolder.class);
		when(folder.getCloud()).thenReturn(cloud);
		Intent intent = Mockito.mock(Intent.class);
		when(intent.getSerializableExtra(Mockito.any())).thenReturn(cloud);
		ActivityResult result = Mockito.mock(ActivityResult.class);
		when(result.intent()).thenReturn(intent);
		GetRootFolderUseCase.Launcher launcher = Mockito.mock(GetRootFolderUseCase.Launcher.class);
		when(getRootFolderUseCase.withCloud(Mockito.any())).thenReturn(launcher);

		inTest.vaultUnlockedVaultList(result);

		ArgumentCaptor<ResultHandler<CloudFolder>> rootFolderHandler = ArgumentCaptor.forClass(ResultHandler.class);
		verify(launcher).run(rootFolderHandler.capture());
		rootFolderHandler.getValue().onSuccess(folder);
	}

	@Test
	public void testOnWindowsFocusChangedTrue() {
		inTest.onWindowFocusChanged(true);

		verify(vaultListView).hideVaultCreationHint();
		verify(getVaultListUseCase).run(Mockito.any());
	}

	@Test
	public void testOnWindowsFocusChangedFalse() {
		inTest.onWindowFocusChanged(false);

		verify(vaultListView, never()).hideVaultCreationHint();
		verify(getVaultListUseCase, never()).run(Mockito.any());
	}

	@Test
	public void testLoadVaultListWithEmptyVaultList() {
		ArgumentCaptor<ResultHandler<List<Vault>>> captor = ArgumentCaptor.forClass(ResultHandler.class);

		inTest.loadVaultList();
		verify(vaultListView).hideVaultCreationHint();
		verify(getVaultListUseCase).run(captor.capture());

		captor.getValue().onSuccess(Collections.emptyList());

		verify(vaultListView).showVaultCreationHint();
	}

	@Test
	public void testLoadVaultListWithVaultList() {
		ArgumentCaptor<ResultHandler<List<Vault>>> captor = ArgumentCaptor.forClass(ResultHandler.class);

		inTest.loadVaultList();
		verify(vaultListView).hideVaultCreationHint();
		verify(getVaultListUseCase).run(captor.capture());

		captor.getValue().onSuccess(asList(AN_UNLOCKED_VAULT, ANOTHER_VAULT_WITH_CLOUD));

		verify(vaultListView, times(2)).hideVaultCreationHint();
		verify(vaultListView).renderVaultList(asList(AN_UNLOCKED_VAULT_MODEL, ANOTHER_VAULT_MODEL_WITH_CLOUD));
	}

	@Test
	public void testDeleteVault() {
		ArgumentCaptor<ResultHandler<Long>> captor = ArgumentCaptor.forClass(ResultHandler.class);

		when(deleteVaultUseCase.withVault(AN_UNLOCKED_VAULT_MODEL.toVault())) //
				.thenReturn(deleteVaultUseCaseLauncher);

		inTest.deleteVault(AN_UNLOCKED_VAULT_MODEL);

		verify(deleteVaultUseCaseLauncher)//
				.run(captor.capture());
		captor.getValue().onSuccess(AN_UNLOCKED_VAULT_MODEL.getVaultId());
		verify(vaultListView).deleteVaultFromAdapter(AN_UNLOCKED_VAULT_MODEL.getVaultId());
	}

	@Test
	public void testRenameVaultWithSuccess() {
		ArgumentCaptor<ResultHandler<Vault>> captor = ArgumentCaptor.forClass(ResultHandler.class);

		when(renameVaultUseCase.withVault(AN_UNLOCKED_VAULT_MODEL.toVault())) //
				.thenReturn(renameVaultUseCaseLauncher);
		when(renameVaultUseCaseLauncher.andNewVaultName(A_NEW_VAULT_NAME)).thenReturn(renameVaultUseCaseLauncher);

		inTest.renameVault(AN_UNLOCKED_VAULT_MODEL, A_NEW_VAULT_NAME);

		verify(renameVaultUseCaseLauncher).run(captor.capture());
		captor.getValue().onSuccess(A_VAULT_WITH_NEW_NAME);
		verify(vaultListView).renameVault(A_VAULT_MODEL_WITH_NEW_NAME);
		verify(vaultListView).closeDialog();
	}

	@Test
	public void testRenameVaultWithError() {
		ArgumentCaptor<ResultHandler<Vault>> captor = ArgumentCaptor.forClass(ResultHandler.class);

		when(renameVaultUseCase.withVault(AN_UNLOCKED_VAULT_MODEL.toVault())) //
				.thenReturn(renameVaultUseCaseLauncher);
		when(renameVaultUseCaseLauncher.andNewVaultName(A_NEW_VAULT_NAME)).thenReturn(renameVaultUseCaseLauncher);

		inTest.renameVault(AN_UNLOCKED_VAULT_MODEL, A_NEW_VAULT_NAME);

		verify(renameVaultUseCaseLauncher).run(captor.capture());
		captor.getValue().onError(AN_EXCEPTION);

		verify(authenticationExceptionHandler)//
				.handleAuthenticationException(Mockito.any(), //
						Mockito.any(), //
						Mockito.any());
	}

}
