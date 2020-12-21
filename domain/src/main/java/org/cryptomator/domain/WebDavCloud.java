package org.cryptomator.domain;

import org.jetbrains.annotations.NotNull;

public class WebDavCloud implements Cloud {

	private final Long id;
	private final String url;
	private final String username;
	private final String password;
	private final String certificate;

	private WebDavCloud(Builder builder) {
		this.id = builder.id;
		this.url = builder.url;
		this.username = builder.username;
		this.password = builder.password;
		this.certificate = builder.certificate;
	}

	@Override
	public Long id() {
		return id;
	}

	@Override
	public boolean configurationMatches(Cloud cloud) {
		return cloud instanceof WebDavCloud && configurationMatches((WebDavCloud) cloud);
	}

	private boolean configurationMatches(WebDavCloud cloud) {
		return url.equals(cloud.url) && username.equals(cloud.username);
	}

	@Override
	public CloudType type() {
		return CloudType.WEBDAV;
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

	public String certificate() {
		return certificate;
	}

	@Override
	public boolean predefined() {
		return false;
	}

	@Override
	public boolean persistent() {
		return true;
	}

	@Override
	public boolean requiresNetwork() {
		return true;
	}

	public static Builder aWebDavCloudCloud() {
		return new Builder();
	}

	public static Builder aCopyOf(WebDavCloud webDavCloud) {
		return new Builder() //
				.withId(webDavCloud.id()) //
				.withUrl(webDavCloud.url()) //
				.withUsername(webDavCloud.username()) //
				.withPassword(webDavCloud.password()) //
				.withCertificate(webDavCloud.certificate());
	}

	@NotNull
	@Override
	public String toString() {
		return "WEBDAV";
	}

	public static class Builder {

		private Long id;
		private String password;
		private String url;
		private String username;
		private String certificate;

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

		public Builder withCertificate(String certificate) {
			this.certificate = certificate;
			return this;
		}

		public WebDavCloud build() {
			return new WebDavCloud(this);
		}

	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || getClass() != obj.getClass())
			return false;
		if (obj == this)
			return true;
		return internalEquals((WebDavCloud) obj);
	}

	@Override
	public int hashCode() {
		return id == null ? 0 : id.hashCode();
	}

	private boolean internalEquals(WebDavCloud obj) {
		return id != null && id.equals(obj.id);
	}

}
