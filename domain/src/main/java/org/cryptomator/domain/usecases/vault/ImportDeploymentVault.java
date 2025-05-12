package org.cryptomator.domain.usecases.vault;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

import androidx.core.content.ContextCompat;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudType;
import org.cryptomator.domain.LocalStorageCloud;
import org.cryptomator.domain.Vault;
import org.cryptomator.domain.model.DeploymentInfo;
import org.cryptomator.domain.repository.CloudRepository;
import org.cryptomator.domain.repository.VaultRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;
import org.cryptomator.util.SharedPreferencesHandler;
import org.cryptomator.domain.model.VaultRemote;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.inject.Inject;

import timber.log.Timber;

@UseCase
public class ImportDeploymentVault {

	private final VaultRepository vaultRepository;
	private final CloudRepository cloudRepository;
	private final List<DeploymentInfo> deployments;
	private final SharedPreferencesHandler sharedPreferencesHandler;
	private final Context context;

	@Inject
	public ImportDeploymentVault(SharedPreferencesHandler sharedPreferencesHandler, VaultRepository vaultRepository, CloudRepository cloudRepository, Context context, @Parameter List<DeploymentInfo> deployments) {
		this.vaultRepository = vaultRepository;
		this.cloudRepository = cloudRepository;
		this.context = context;
		this.deployments = deployments;
		this.sharedPreferencesHandler = sharedPreferencesHandler;
	}

	public void execute() throws Exception {
		try {
			// Check and request storage permissions
			if (!hasStoragePermissions()) {
				throw new Exception("Storage permissions are required to import vaults.");
			}

			// DEBUG: Log all existing vaults first
			Timber.d("--------- EXISTING VAULTS ---------");
			for (Vault existingVault : vaultRepository.vaults()) {
				logVaultDetails(existingVault, "EXISTING");
			}

			// Process each deployment
			for (DeploymentInfo deployment : deployments) {
				try {
					// Get volume path from computers associated with this vault
					Optional<String> volumePath = Objects.requireNonNull(deployment.getVaultRemote().getComputers()).stream()
							.filter(computer -> computer.getVolumePath() != null && !computer.getVolumePath().isEmpty())
							.map(VaultRemote.Computer::getVolumePath)
							.findFirst();

					if (!volumePath.isPresent()) {
						Timber.d("Skipping deployment %s - no valid volume path", deployment.getVaultRemote().getDeviceId());
						continue;
					}

					// Create vault name from deployment info
					String vaultName = deployment.getVaultRemote().getVolumeName();

					// Use volumePath as the base folder (e.g., "Upwork-sample")
					String baseFolder = volumePath.get();

					// Use volumeName directly as the vault folder name
					String vaultFolderName = vaultName;

					Timber.d("ImportVault: Extracted components - vaultName: %s, baseFolder: %s", 
							vaultFolderName, baseFolder);

					// EXACT REQUIRED VALUES:
					// FOLDER_PATH: /math - name of the vault with "/" prefix (single slash)
					String folderPath = "/" + vaultFolderName;

					// FOLDER_NAME: math - name of the vault
					String folderName = vaultFolderName;

					// FULL_LOCAL_PATH: Upwork-sample - just the base folder
					String fullLocalPath = baseFolder;

					Timber.d("ImportVault: Using required values - FOLDER_PATH: %s, FOLDER_NAME: %s, FULL_LOCAL_PATH: %s", 
							folderPath, folderName, fullLocalPath);

					// Create base directory in external storage using the baseFolder name from volumePath
					File baseDir = new File(Environment.getExternalStorageDirectory(), baseFolder);
					if (!baseDir.exists()) {
						boolean created = baseDir.mkdirs();
						if (!created) {
							Timber.e("Failed to create base vault directory: %s", baseDir.getAbsolutePath());
							continue;
						}
					}
					Timber.d("ImportVault: baseDir created at: %s", baseDir.getAbsolutePath());

					// Ensure we have permissions to access this directory
					ensureDirectoryPermissions(baseDir);

					// Create the vault directory inside the base directory
					// This is where the actual vault files will be stored
					String vaultDirName = vaultFolderName.replaceAll("[^a-zA-Z0-9]", "_").toLowerCase();
					File vaultDir = new File(baseDir, vaultDirName);

					// Create the vault directory
					if (!vaultDir.exists()) {
						boolean created = vaultDir.mkdir();
						if (!created) {
							Timber.e("Failed to create vault directory: %s", vaultDir.getAbsolutePath());
							continue;
						}
					}

					Timber.d("ImportVault: Created vault directory at: %s", vaultDir.getAbsolutePath());

					// Ensure the vault directory is accessible and has correct permissions
					ensureDirectoryPermissions(vaultDir);
					if (!vaultDir.canRead() || !vaultDir.canWrite()) {
						Timber.e("ImportVault: Cannot read/write to vault directory: %s", vaultDir.getAbsolutePath());
						continue;
					}

					// Save vault files
					// 1. Save masterkey file (masterkey.ncryptor)
					byte[] vaultMKData = Base64.getDecoder().decode(deployment.getVaultInfo().getVaultMK());
					File masterkeyFile = new File(vaultDir, "masterkey.cryptomator");
					try (FileOutputStream fos = new FileOutputStream(masterkeyFile)) {
						fos.write(vaultMKData);
					}
					Timber.d("ImportVault: Created masterkey file: %s, Size: %d bytes", masterkeyFile.getAbsolutePath(), masterkeyFile.length());

					// 2. Save vault config file (vault.cryptomator)
					String vaultData = deployment.getVaultInfo().getVaultData();
					File vaultConfigFile = new File(vaultDir, "vault.cryptomator");
					try (FileOutputStream fos = new FileOutputStream(vaultConfigFile)) {
						fos.write(vaultData.getBytes());
					}
					Timber.d("ImportVault: Created vault config file: %s, Size: %d bytes", vaultConfigFile.getAbsolutePath(), vaultConfigFile.length());

					Timber.d("ImportVault: Created vault files in: %s", vaultDir.getAbsolutePath());

					// Check that vault files were created successfully
					if (!masterkeyFile.exists() || !vaultConfigFile.exists()) {
						Timber.e("ImportVault: Vault files creation failed. Masterkey exists: %s, Vault config exists: %s", masterkeyFile.exists(), vaultConfigFile.exists());
						continue;
					}

					// For SAF URI, construct using just the base folder
					// This URI format is: content://com.android.externalstorage.documents/tree/primary%3AUpwork-sample
					String uriPath = baseFolder;

					// Create the SAF URI with the proper format
					// Format: content://com.android.externalstorage.documents/tree/primary%3AUpwork-sample
					String safUri = "content://com.android.externalstorage.documents/tree/primary%3A" + baseFolder;

					Timber.d("ImportVault: Using SAF URI format: %s for vault path: %s", safUri, uriPath);

					// Create the cloud with the correctly encoded URI format
					// IMPORTANT: First create and store the cloud entity in the database
					// so it gets an ID that can be referenced by the vault
					Cloud cloud = LocalStorageCloud.aLocalStorage().withRootUri(safUri).build();

					// Store the cloud in CloudRepository
					try {
						// Explicitly store the cloud to get an ID assigned
						cloud = cloudRepository.store(cloud);
						Timber.d("ImportVault: Successfully stored cloud with ID: %s, URI: %s", cloud.id(), safUri);
					} catch (Exception e) {
						Timber.e(e, "ImportVault: Failed to store cloud entity");
						continue;
					}

					// Build the vault with the precisely correct parameters to ensure proper navigation
					Vault vault = Vault.aVault().thatIsNew().withName(folderName)  // FOLDER_NAME: math
							.withPath(folderPath)  // FOLDER_PATH: /math
							.withCloudType(CloudType.LOCAL).withCloud(cloud).withUnlocked(false).withFormat(-1) // Same as in CreateVault
							.withShorteningThreshold(-1) // Same as in CreateVault
							.withPosition(vaultRepository.vaults().size()).build();

					// Now that vault is defined, we can log its details
					Timber.d("--------- BEFORE IMPORT ---------");
					logVaultDetails(vault, "BEFORE");

					vault = vaultRepository.store(vault);

					Timber.d("--------- AFTER IMPORT ---------");
					logVaultDetails(vault, "AFTER");

					// Add debug logging to confirm cloud is still attached
					Timber.d("ImportVault: Vault stored with ID: %d, cloud null? %s, path: %s", vault.getId(), (vault.getCloud() == null ? "yes" : "no"), vault.getPath());

					Timber.d("ImportVault: Successfully imported vault: %s", vaultName);

				} catch (IOException e) {
					Timber.e(e, "Failed to save vault files for deployment: %s", deployment.getVaultRemote().getDeviceId());
				}
			}

			// DEBUG: Log all vaults again after import
			Timber.d("--------- ALL VAULTS AFTER IMPORT ---------");
			for (Vault existingVault : vaultRepository.vaults()) {
				logVaultDetails(existingVault, "FINAL");
			}

		} catch (Exception e) {
			Timber.e(e, "Failed to import deployment vaults");
			throw new Exception("Failed to import deployment vaults");
		}
	}

	// Helper method to log detailed vault properties for debugging
	private void logVaultDetails(Vault vault, String stage) {
		Timber.d("Vault [%s] - ID: %d, Name: %s", stage, vault.getId(), vault.getName());
		Timber.d("Vault [%s] - Path: %s", stage, vault.getPath());
		Timber.d("Vault [%s] - DeviceID: %s", stage, vault.getId());
		Timber.d("Vault [%s] - Format: %d, ShorteningThreshold: %d", stage, vault.getFormat(), vault.getShorteningThreshold());
		Timber.d("Vault [%s] - Position: %d", stage, vault.getPosition());

		if (vault.getCloud() != null) {
			Cloud cloud = vault.getCloud();
			Timber.d("Vault [%s] - Cloud Type: %s", stage, vault.getCloudType());
			Timber.d("Vault [%s] - Cloud ID: %s", stage, cloud.id());
			if (cloud instanceof LocalStorageCloud) {
				LocalStorageCloud localCloud = (LocalStorageCloud) cloud;
				Timber.d("Vault [%s] - Cloud URI: %s", stage, localCloud.rootUri());
			}
		} else {
			Timber.d("Vault [%s] - Cloud: NULL", stage);
		}
	}

	private void ensureDirectoryPermissions(File directory) {
		if (!directory.exists() || !directory.canRead() || !directory.canWrite()) {
			Timber.d("ImportVault: Permission issues with directory: %s", directory.getAbsolutePath());
		} else {
			Timber.d("ImportVault: Directory permissions OK: %s", directory.getAbsolutePath());
		}
	}


	private boolean hasStoragePermissions() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			return Environment.isExternalStorageManager();
		} else {
			return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
		}
	}
}