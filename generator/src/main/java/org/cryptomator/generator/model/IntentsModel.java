package org.cryptomator.generator.model;

import java.util.ArrayList;
import java.util.List;

public class IntentsModel {

	private final List<IntentBuilderModel> builders;
	private final List<IntentReaderModel> readers;

	public static IntentsModel.Builder builder() {
		return new Builder();
	}

	private IntentsModel(List<IntentBuilderModel> builders, List<IntentReaderModel> readers) {
		this.builders = builders;
		this.readers = readers;
	}

	public List<IntentBuilderModel> getBuilders() {
		return builders;
	}

	public List<IntentReaderModel> getReaders() {
		return readers;
	}

	public String getJavaPackage() {
		return "org.cryptomator.presentation.intent";
	}

	public String getClassName() {
		return "Intents";
	}

	public static class Builder {

		private final List<IntentBuilderModel> builders = new ArrayList<>();
		private final List<IntentReaderModel> readers = new ArrayList<>();

		private Builder() {
		}

		public Builder add(IntentBuilderModel builder) {
			builders.add(builder);
			return this;
		}

		public Builder add(IntentReaderModel reader) {
			readers.add(reader);
			return this;
		}

		public IntentsModel build() {
			return new IntentsModel(builders, readers);
		}

	}

}
