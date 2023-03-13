package org.cryptomator.generator.model;

import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class FragmentsModel {

	private final SortedSet<FragmentModel> fragments;

	private FragmentsModel(SortedSet<FragmentModel> fragments) {
		this.fragments = fragments;
	}

	public static FragmentsModel.Builder builder() {
		return new Builder();
	}

	public Set<FragmentModel> getFragments() {
		return fragments;
	}

	public String getJavaPackage() {
		return "org.cryptomator.presentation.ui.fragment";
	}

	public String getClassName() {
		return "Fragments";
	}

	public static class Builder {

		private final SortedSet<FragmentModel> fragments = new TreeSet<>();

		private Builder() {
		}

		public Builder add(FragmentModel fragment) {
			fragments.add(fragment);
			return this;
		}

		public FragmentsModel build() {
			return new FragmentsModel(fragments);
		}

	}

}
