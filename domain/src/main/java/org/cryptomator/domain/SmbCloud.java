package org.cryptomator.domain;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * SMB Cloud implementation.
 * Currently just a skeleton for the first step of SMB support.
 */
public class SmbCloud implements Cloud {

	private final Long id;
	private final String url;
	private final String username;
	private final String password;

	private SmbCloud(Builder builder) {
		this.id = builder.id;
		this.url = builder.url;
		this.username = builder.username;
		this.password = builder.password;
	}

	public static Builder aSmbCloud() {
		return new Builder();
	}

	public static Builder aCopyOf(SmbCloud smbCloud) {
		return new Builder() //
				.withId(smbCloud.id()) //
				.withUrl(smbCloud.url()) //
				.withUsername(smbCloud.username()) //
				.withPassword(smbCloud.password());
	}

	@Override
	public Long id() {
		return id;
	}

	@Override
	public boolean configurationMatches(Cloud cloud) {
		return cloud instanceof SmbCloud && configurationMatches((SmbCloud) cloud);
	}

	private boolean configurationMatches(SmbCloud cloud) {
		return Objects.equals(url, cloud.url) && Objects.equals(username, cloud.username);
	}

	@Override
	public CloudType type() {
		return CloudType.SMB;
	}

	public String password() {
		return password;
	}

	public String url() {
		return url;
	}

	public String username() {
		return username;
	}

	@Override
	public boolean persistent() {
		return true;
	}

	@Override
	public boolean requiresNetwork() {
		return true;
	}

	@Override
	public boolean isReadOnly() {
		return false;
	}

	@NotNull
	@Override
	public String toString() {
		return "SMB";
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		if (obj == this) {
			return true;
		}
		return internalEquals((SmbCloud) obj);
	}

	@Override
	public int hashCode() {
		return id == null ? 0 : id.hashCode();
	}

	private boolean internalEquals(SmbCloud obj) {
		return Objects.equals(id, obj.id);
	}

	public static class Builder {

		private Long id;
		private String password;
		private String url;
		private String username;

		private Builder() {
		}

		public Builder withId(Long id) {
			this.id = id;
			return this;
		}

		public Builder withUsername(String username) {
			this.username = username;
			return this;
		}

		public Builder withPassword(String password) {
			this.password = password;
			return this;
		}

		public Builder withUrl(String url) {
			this.url = url;
			return this;
		}

		public SmbCloud build() {
			return new SmbCloud(this);
		}

	}

}
