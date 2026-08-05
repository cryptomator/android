package org.cryptomator.domain;

import org.jetbrains.annotations.NotNull;

import java.io.Serial;
import java.util.Objects;

/**
 * Represents an SMB (Server Message Block) cloud storage configuration.
 * Stores connection details like URL, credentials, and domain.
 */
public class SmbCloud implements Cloud {

	@Serial
	private static final long serialVersionUID = 421064219462828478L;

	private final Long id;
	private final String url;
	private final String username;
	private final String password;
	private final String domain;

	private SmbCloud(Builder builder) {
		this.id = builder.id;
		this.url = builder.url;
		this.username = builder.username;
		this.password = builder.password;
		this.domain = builder.domain;
	}

	public static Builder aSmbCloud() {
		return new Builder();
	}

	public static Builder aCopyOf(SmbCloud smbCloud) {
		return new Builder() //
				.withId(smbCloud.id()) //
				.withUrl(smbCloud.url()) //
				.withUsername(smbCloud.username()) //
				.withPassword(smbCloud.password()) //
				.withDomain(smbCloud.domain());
	}

	@Override
	public Long id() {
		return id;
	}

	@Override
	public boolean configurationMatches(Cloud cloud) {
		return cloud instanceof SmbCloud smbCloud && configurationMatches(smbCloud);
	}

	private boolean configurationMatches(SmbCloud cloud) {
		return Objects.equals(url, cloud.url) && Objects.equals(username, cloud.username) && Objects.equals(domain, cloud.domain);
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

	public String domain() {
		return domain;
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
		private String domain;

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

		public Builder withDomain(String domain) {
			this.domain = domain;
			return this;
		}

		public SmbCloud build() {
			return new SmbCloud(this);
		}

	}

}
