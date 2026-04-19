package org.cryptomator.domain;

import org.cryptomator.util.crypto.CryptoMode;

import java.io.Serializable;

public class Vault implements Serializable {

	private static final Long NOT_SET = Long.MIN_VALUE;
	private final Long id;
	private final String name;
	private final String path;
	private final Cloud cloud;
	private final CloudType cloudType;
	private final boolean unlocked;
	private final String password;
	private final CryptoMode passwordCryptoMode;
	private final int format;
	private final int shorteningThreshold;
	private final int position;
	private final boolean hubVault;
	private final boolean hubPaidLicense;

	/**
	 * Creates a Vault instance from the given Builder's validated state.
	 *
	 * @param builder the Builder containing the validated properties to initialize this Vault
	 */
	private Vault(Builder builder) {
		this.id = builder.id;
		this.name = builder.name;
		this.path = builder.path;
		this.cloud = builder.cloud;
		this.unlocked = builder.unlocked;
		this.cloudType = builder.cloudType;
		this.password = builder.password;
		this.passwordCryptoMode = builder.passwordCryptoMode;
		this.format = builder.format;
		this.shorteningThreshold = builder.shorteningThreshold;
		this.position = builder.position;
		this.hubVault = builder.hubVault;
		this.hubPaidLicense = builder.hubPaidLicense;
	}

	/**
	 * Create a new Builder for configuring and constructing a Vault.
	 *
	 * @return a Builder instance initialized with default values for building a Vault
	 */
	public static Builder aVault() {
		return new Builder();
	}

	/**
	 * Create a Builder pre-populated from an existing Vault.
	 *
	 * The returned Builder is initialized with the vault's id, cloud, cloudType, name, path,
	 * unlocked state, saved password and its crypto mode, format, shortening threshold, position,
	 * and hub-related flags.
	 *
	 * @param vault the source Vault to copy properties from
	 * @return a Builder initialized with the source vault's properties
	 */
	public static Builder aCopyOf(Vault vault) {
		return new Builder() //
				.withId(vault.getId()) //
				.withCloud(vault.getCloud()) //
				.withCloudType(vault.getCloudType()) //
				.withName(vault.getName()) //
				.withPath(vault.getPath()) //
				.withUnlocked(vault.isUnlocked()) //
				.withSavedPassword(vault.getPassword(), vault.getPasswordCryptoMode()) //
				.withFormat(vault.getFormat()) //
				.withShorteningThreshold(vault.getShorteningThreshold()) //
				.withPosition(vault.getPosition()) //
				.withHubVault(vault.isHubVault()) //
				.withHubPaidLicense(vault.hasHubPaidLicense());
	}

	/**
	 * Gets the vault's identifier.
	 *
	 * @return the vault id, or {@code null} if the vault has no assigned identifier
	 */
	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getPath() {
		return path;
	}

	public Cloud getCloud() {
		return cloud;
	}

	public CloudType getCloudType() {
		return cloudType;
	}

	public boolean isUnlocked() {
		return unlocked;
	}

	public String getPassword() {
		return password;
	}

	public CryptoMode getPasswordCryptoMode() {
		return passwordCryptoMode;
	}

	public int getFormat() {
		return format;
	}

	public int getShorteningThreshold() {
		return shorteningThreshold;
	}

	public int getPosition() {
		return position;
	}

	/**
	 * Indicates whether this vault is read-only.
	 *
	 * @return `true` if the vault is read-only, `false` otherwise.
	 */
	public boolean isReadOnly() {
		return false; //TODO Implement read-only check
	}

	/**
	 * Indicates whether this vault is managed by Hub.
	 *
	 * @return true if the vault is a Hub vault, false otherwise.
	 */
	public boolean isHubVault() {
		return hubVault;
	}

	/**
	 * Indicates whether the vault has an associated Hub paid license.
	 *
	 * @return `true` if the vault has a Hub paid license, `false` otherwise.
	 */
	public boolean hasHubPaidLicense() {
		return hubPaidLicense;
	}

	/**
	 * Determines whether the given object represents the same Vault, using instance identity or matching non-null vault id.
	 *
	 * @param obj the object to compare with this Vault
	 * @return `true` if {@code obj} is the same instance or a {@code Vault} whose non-null id equals this vault's id, `false` otherwise
	 */
	@Override
	public boolean equals(Object obj) {
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		if (obj == this) {
			return true;
		}
		return internalEquals((Vault) obj);
	}

	private boolean internalEquals(Vault obj) {
		return id != null && id.equals(obj.id);
	}

	@Override
	public int hashCode() {
		return id == null ? 0 : id.hashCode();
	}

	public static class Builder {

		private Long id = NOT_SET;
		private String name;
		private String path;
		private Cloud cloud;
		private CloudType cloudType;
		private boolean unlocked;
		private String password;
		private CryptoMode passwordCryptoMode;
		private int format = -1;
		private int shorteningThreshold = -1;
		private int position = -1;
		private boolean hubVault;
		private boolean hubPaidLicense;

		/**
		 * Creates a new Builder initialized with default field values.
		 */
		private Builder() {
		}

		public Builder thatIsNew() {
			this.id = null;
			return this;
		}

		public Builder withId(Long id) {
			if (id < 1) {
				throw new IllegalArgumentException("id must not be smaller one");
			}
			this.id = id;
			return this;
		}

		public Builder withName(String name) {
			this.name = name;
			return this;
		}

		public Builder withPath(String path) {
			this.path = path;
			return this;
		}

		public Builder withUnlocked(boolean unlocked) {
			this.unlocked = unlocked;
			return this;
		}

		public Builder withCloud(Cloud cloud) {
			this.cloud = cloud;

			if (cloud != null) {
				this.cloudType = cloud.type();
			}

			return this;
		}

		public Builder withCloudType(CloudType cloudType) {
			this.cloudType = cloudType;

			if (cloud != null && cloud.type() != cloudType) {
				throw new IllegalStateException("Cloud type must match cloud");
			}

			return this;
		}

		public Builder withNamePathAndCloudFrom(CloudFolder vaultFolder) {
			this.name = vaultFolder.getName();
			this.path = vaultFolder.getPath();
			this.cloud = vaultFolder.getCloud();
			this.cloudType = cloud.type();
			return this;
		}

		public Builder withSavedPassword(String password, CryptoMode cryptoMode) {
			this.password = password;
			this.passwordCryptoMode = cryptoMode;
			return this;
		}

		public Builder withFormat(int version) {
			this.format = version;
			return this;
		}

		/**
		 * Sets the filename shortening threshold for the vault being built.
		 *
		 * @param shorteningThreshold the threshold value at which shortening is applied (use -1 to leave unset)
		 * @return this builder instance
		 */
		public Builder withShorteningThreshold(int shorteningThreshold) {
			this.shorteningThreshold = shorteningThreshold;
			return this;
		}

		/**
		 * Sets whether the vault has an associated Hub paid license.
		 *
		 * @param hubPaidLicense true if the vault has a Hub paid license, false otherwise
		 * @return this Builder instance for chaining
		 */
		public Builder withHubPaidLicense(boolean hubPaidLicense) {
			this.hubPaidLicense = hubPaidLicense;
			return this;
		}

		/**
		 * Marks the vault under construction as a Hub vault.
		 *
		 * @param hubVault `true` to mark the vault as a Hub-managed vault, `false` otherwise
		 * @return this builder
		 */
		public Builder withHubVault(boolean hubVault) {
			this.hubVault = hubVault;
			return this;
		}

		/**
		 * Sets the vault's position used for ordering.
		 *
		 * @param position numeric position used for ordering; must be set to a value other than -1 before calling {@code build()}
		 * @return this Builder instance
		 */
		public Builder withPosition(int position) {
			this.position = position;
			return this;
		}

		public Vault build() {
			validate();
			return new Vault(this);
		}

		private void validate() {
			if (NOT_SET.equals(id)) {
				throw new IllegalStateException("id must be set");
			}
			if (name == null) {
				throw new IllegalStateException("name must be set");
			}
			if (path == null) {
				throw new IllegalStateException("path must be set");
			}
			if (cloudType == null) {
				throw new IllegalStateException("cloudtype must be set");
			}
			if (position == -1) {
				throw new IllegalStateException("position must be set");
			}
			if (password != null && passwordCryptoMode == null) {
				throw new IllegalStateException("passwordCryptoMode must be set if password is set");
			}
			if (passwordCryptoMode != null && password == null) {
				throw new IllegalStateException("password must be set if passwordCryptoMode is set");
			}
		}
	}
}
