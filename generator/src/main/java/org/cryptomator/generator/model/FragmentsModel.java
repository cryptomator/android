package org.cryptomator.generator.model;

import java.util.ArrayList;
import java.util.List;

public class FragmentsModel {

	private final List<FragmentModel> fragments;

	public static FragmentsModel.Builder builder() {
		return new Builder();
	}

	private FragmentsModel(List<FragmentModel> fragments) {
		this.fragments = fragments;
	}

	public List<FragmentModel> getFragments() {
		return fragments;
	}

	public String getJavaPackage() {
		return "org.cryptomator.presentation.ui.fragment";
	}

	public String getClassName() {
		return "Fragments";
	}

	public static class Builder {

		private final List<FragmentModel> fragments = new ArrayList<>();

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
