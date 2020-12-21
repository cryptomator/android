package org.cryptomator.domain.usecases.cloud;

public class UploadFile {

	private final String fileName;

	private final DataSource dataSource;

	private final Boolean replacing;

	private UploadFile(Builder builder) {
		this.fileName = builder.fileName;
		this.dataSource = builder.dataSource;
		this.replacing = builder.replacing;
	}

	public String getFileName() {
		return fileName;
	}

	public DataSource getDataSource() {
		return dataSource;
	}

	public Boolean getReplacing() {
		return replacing;
	}

	public static Builder aCopyOf(UploadFile uploadFile) {
		return new Builder() //
				.withFileName(uploadFile.getFileName()) //
				.withDataSource(uploadFile.getDataSource()) //
				.thatIsReplacing(uploadFile.getReplacing());
	}

	public static Builder anUploadFile() {
		return new Builder();
	}

	public static class Builder {

		private String fileName;
		private DataSource dataSource;
		private Boolean replacing;

		public Builder() {
		}

		public Builder withDataSource(DataSource dataSource) {
			this.dataSource = dataSource;
			return this;
		}

		public Builder withFileName(String fileName) {
			this.fileName = fileName;
			return this;
		}

		public Builder thatIsReplacing(Boolean replacing) {
			this.replacing = replacing;
			return this;
		}

		public UploadFile build() {
			return new UploadFile(this);
		}
	}
}
