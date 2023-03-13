package org.cryptomator.generator.model;

import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class IntentsModel {

	private final SortedSet<IntentBuilderModel> builders;
	private final SortedSet<IntentReaderModel> readers;

	private IntentsModel(SortedSet<IntentBuilderModel> builders, SortedSet<IntentReaderModel> readers) {
		this.builders = builders;
		this.readers = readers;
	}

	public static IntentsModel.Builder builder() {
		return new Builder();
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

		private final SortedSet<IntentBuilderModel> builders = new TreeSet<>();
		private final SortedSet<IntentReaderModel> readers = new TreeSet<>();

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
