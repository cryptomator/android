package org.cryptomator.generator.model;

import java.util.ArrayList;
import java.util.List;

public class ActivitiesModel {

	private final List<ActivityModel> activities;

	public static ActivitiesModel.Builder builder() {
		return new Builder();
	}

	private ActivitiesModel(List<ActivityModel> activities) {
		this.activities = activities;
	}

	public List<ActivityModel> getActivities() {
		return activities;
	}

	public String getJavaPackage() {
		return "org.cryptomator.presentation.ui.activity";
	}

	public String getClassName() {
		return "Activities";
	}

	public static class Builder {

		private final List<ActivityModel> activities = new ArrayList<>();

		private Builder() {
		}

		public Builder add(ActivityModel activity) {
			activities.add(activity);
			return this;
		}

		public ActivitiesModel build() {
			return new ActivitiesModel(activities);
		}

	}

}
