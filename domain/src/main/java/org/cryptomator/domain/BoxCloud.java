package org.cryptomator.domain;

import org.jetbrains.annotations.NotNull;

public class BoxCloud implements Cloud {

	private final Long id;
	private final String userId;
	private final String username;

	private BoxCloud(Builder builder) {
		this.id = builder.id;
		this.userId = builder.userId;
		this.username = builder.username;
	}

	public static Builder aBoxCloud() {
		return new Builder();
	}

	public static Builder aCopyOf(BoxCloud boxCloud) {
		return new Builder() //
				.withId(boxCloud.id()) //
				.withUserId(boxCloud.userId()) //
				.withUsername(boxCloud.username());
	}

	@Override
	public Long id() {
		return id;
	}

	/**
	 * Id of the Box account, which unlike its login email can't change.
	 */
	public String userId() {
		return userId;
	}

	public String username() {
		return username;
	}

	@Override
	public CloudType type() {
		return CloudType.BOX;
	}

	@Override
	public boolean configurationMatches(Cloud cloud) {
		return cloud instanceof BoxCloud && configurationMatches((BoxCloud) cloud);
	}

	private boolean configurationMatches(BoxCloud cloud) {
		return userId != null && userId.equals(cloud.userId);
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
		return "BOX";
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		if (obj == this) {
			return true;
		}
		return internalEquals((BoxCloud) obj);
	}

	@Override
	public int hashCode() {
		return id == null ? 0 : id.hashCode();
	}

	private boolean internalEquals(BoxCloud obj) {
		return id != null && id.equals(obj.id);
	}

	public static class Builder {

		private Long id;
		private String userId;
		private String username;

		private Builder() {
		}

		public Builder withId(Long id) {
			this.id = id;
			return this;
		}

		public Builder withUserId(String userId) {
			this.userId = userId;
			return this;
		}

		public Builder withUsername(String username) {
			this.username = username;
			return this;
		}

		public BoxCloud build() {
			return new BoxCloud(this);
		}

	}

}
