package org.cryptomator.generator.model;

import java.util.Set;
import java.util.TreeSet;

public class IntentsModel {

	private final Set<IntentBuilderModel> builders;
	private final Set<IntentReaderModel> readers;

	public static IntentsModel.Builder builder() {
		return new Builder();
	}

	private IntentsModel(Set<IntentBuilderModel> builders, Set<IntentReaderModel> readers) {
		this.builders = builders;
		this.readers = readers;
	}

	public Set<IntentBuilderModel> getBuilders() {
		return builders;
	}

	public Set<IntentReaderModel> getReaders() {
		return readers;
	}

	public String getJavaPackage() {
		return "org.cryptomator.presentation.intent";
	}

	public String getClassName() {
		return "Intents";
	}

	public static class Builder {

		private final Set<IntentBuilderModel> builders = new TreeSet<>();
		private final Set<IntentReaderModel> readers = new TreeSet<>();

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
