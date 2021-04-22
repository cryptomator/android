package org.cryptomator.domain;

import org.jetbrains.annotations.NotNull;

public class S3Cloud implements Cloud {

	private final Long id;
	private final String accessKey;
	private final String secretKey;
	private final String s3Bucket;
	private final String s3Endpoint;
	private final String s3Region;
	private final String displayName;

	private S3Cloud(Builder builder) {
		this.id = builder.id;
		this.accessKey = builder.accessKey;
		this.secretKey = builder.secretKey;
		this.s3Bucket = builder.s3Bucket;
		this.s3Endpoint = builder.s3Endpoint;
		this.s3Region = builder.s3Region;
		this.displayName = builder.displayName;
	}

	public static Builder aS3Cloud() {
		return new Builder();
	}

	public static Builder aCopyOf(S3Cloud s3Cloud) {
		return new Builder() //
				.withId(s3Cloud.id()) //
				.withAccessKey(s3Cloud.accessKey()) //
				.withSecretKey(s3Cloud.secretKey()) //
				.withS3Bucket(s3Cloud.s3Bucket()) //
				.withS3Endpoint(s3Cloud.s3Endpoint()) //
				.withS3Region(s3Cloud.s3Region()) //
				.withDisplayName(s3Cloud.displayName());
	}

	@Override
	public Long id() {
		return id;
	}

	public String accessKey() {
		return accessKey;
	}

	public String secretKey() {
		return secretKey;
	}

	public String s3Bucket() {
		return s3Bucket;
	}

	public String s3Endpoint() {
		return s3Endpoint;
	}

	public String s3Region() {
		return s3Region;
	}

	public String displayName() {
		return displayName;
	}

	@Override
	public CloudType type() {
		return CloudType.S3;
	}

	@Override
	public boolean configurationMatches(Cloud cloud) {
		return cloud instanceof S3Cloud && configurationMatches((S3Cloud) cloud);
	}

	private boolean configurationMatches(S3Cloud cloud) {
		//FIXME: figure out when it is necessary to create a new cloud
		return s3Bucket.equals(cloud.s3Bucket) && s3Endpoint.equals(cloud.s3Endpoint) && s3Region.equals(cloud.s3Region);
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

	@NotNull
	@Override
	public String toString() {
		return "S3";
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		if (obj == this) {
			return true;
		}
		return internalEquals((S3Cloud) obj);
	}

	@Override
	public int hashCode() {
		return id == null ? 0 : id.hashCode();
	}

	private boolean internalEquals(S3Cloud obj) {
		return id != null && id.equals(obj.id);
	}

	public static class Builder {

		private Long id;
		private String accessKey;
		private String secretKey;
		private String s3Bucket;
		private String s3Endpoint;
		private String s3Region;
		private String displayName;

		private Builder() {
		}

		public Builder withId(Long id) {
			this.id = id;
			return this;
		}

		public Builder withAccessKey(String accessKey) {
			this.accessKey = accessKey;
			return this;
		}

		public Builder withSecretKey(String secretKey) {
			this.secretKey = secretKey;
			return this;
		}

		public Builder withS3Bucket(String s3Bucket) {
			this.s3Bucket = s3Bucket;
			return this;
		}

		public Builder withS3Endpoint(String s3Endpoint) {
			this.s3Endpoint = s3Endpoint;
			return this;
		}

		public Builder withS3Region(String s3Region) {
			this.s3Region = s3Region;
			return this;
		}

		public Builder withDisplayName(String displayName) {
			this.displayName = displayName;
			return this;
		}

		public S3Cloud build() {
			return new S3Cloud(this);
		}

	}

}
