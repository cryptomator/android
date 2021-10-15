package org.cryptomator.presentation.intent;

import androidx.annotation.Nullable;

import org.cryptomator.presentation.model.CloudFolderModel;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.cryptomator.presentation.intent.ChooseCloudNodeSettings.SelectionMode.FILES_ONLY;
import static org.cryptomator.presentation.intent.ChooseCloudNodeSettings.SelectionMode.FOLDERS_ONLY;

public class ChooseCloudNodeSettings implements Serializable {

	public static final int NO_ICON = -1;
	private static final Pattern ANY_NAME = Pattern.compile(".*");
	private static final Pattern NO_NAME = Pattern.compile("");
	private final String extraTitle;
	private final String extraText;
	private final String buttonText;

	private final SelectionMode selectionMode;
	private final Pattern namePattern;
	private final Pattern excludeFoldersContainingNamePattern;
	private final int extraToolbarIcon;
	private final NavigationMode navigationMode;
	private final List<CloudFolderModel> excludeFolders;
	private final List<String> excludeFolderContainingNames;

	private ChooseCloudNodeSettings(Builder builder) {
		this.extraTitle = builder.extraTitle;
		this.extraText = builder.extraText;
		this.buttonText = builder.buttonText;
		this.namePattern = builder.namePattern;
		this.selectionMode = builder.selectionMode;
		this.excludeFolderContainingNames = builder.excludeFolderContainingNames;
		this.excludeFoldersContainingNamePattern = builder.excludeFoldersContainingNamePattern;
		this.excludeFolders = builder.excludeFolders;
		this.extraToolbarIcon = builder.extraToolbarIcon;
		this.navigationMode = builder.navigationMode;
	}

	public static Builder chooseCloudNodeSettings() {
		return new Builder();
	}

	@Nullable
	public String extraTitle() {
		return extraTitle;
	}

	public String extraText() {
		return extraText;
	}

	@Nullable
	public String buttonText() {
		return buttonText;
	}

	public Pattern namePattern() {
		return namePattern;
	}

	public List<String> getExcludeFolderContainingNames() {
		return excludeFolderContainingNames;
	}

	public Pattern excludeFoldersContainingNamePattern() {
		return excludeFoldersContainingNamePattern;
	}

	public boolean excludeFolder(CloudFolderModel cloudFolder) {
		return excludeFolders != null && excludeFolders.contains(cloudFolder);
	}

	public int extraToolbarIcon() {
		return extraToolbarIcon;
	}

	public SelectionMode selectionMode() {
		return selectionMode;
	}

	public NavigationMode navigationMode() {
		return navigationMode;
	}

	public enum SelectionMode {
		FILES_ONLY(true, true), FOLDERS_ONLY(false, true);

		private final boolean allowsFolders;
		private final boolean allowsFiles;

		SelectionMode(boolean allowsFiles, boolean allowsFolders) {
			this.allowsFiles = allowsFiles;
			this.allowsFolders = allowsFolders;
		}

		public boolean allowsFolders() {
			return allowsFolders;
		}

		public boolean allowsFiles() {
			return allowsFiles;
		}

	}

	public enum NavigationMode {
		BROWSE_FILES, MOVE_CLOUD_NODE, SELECT_ITEMS
	}

	public static class Builder {

		private final Pattern excludeFoldersContainingNamePattern = NO_NAME;
		private String extraTitle;
		private String extraText;
		private String buttonText;
		private SelectionMode selectionMode;
		private Pattern namePattern = ANY_NAME;
		private int extraToolbarIcon = NO_ICON;
		private NavigationMode navigationMode = NavigationMode.BROWSE_FILES;
		private List<CloudFolderModel> excludeFolders;
		private List<String> excludeFolderContainingNames = new ArrayList<>();

		public Builder withExtraTitle(String extraTitle) {
			if (extraTitle == null) {
				throw new IllegalArgumentException();
			}
			this.extraTitle = extraTitle;
			return this;
		}

		public Builder withExtraText(String extraText) {
			if (extraText == null) {
				throw new IllegalArgumentException();
			}
			this.extraText = extraText;
			return this;
		}

		public Builder withButtonText(String buttonText) {
			if (buttonText == null) {
				throw new IllegalArgumentException();
			}
			this.buttonText = buttonText;
			return this;
		}

		public Builder withExtraToolbarIcon(int extraToolbarIcon) {
			this.extraToolbarIcon = extraToolbarIcon;
			return this;
		}

		public Builder withNavigationMode(NavigationMode navigationMode) {
			this.navigationMode = navigationMode;
			return this;
		}

		public Builder selectingFileWithNameOnly(String name) {
			this.selectionMode = FILES_ONLY;
			this.namePattern = Pattern.compile(Pattern.quote(name));
			return this;
		}

		public Builder selectingFilesWithNameOnly(List<String> names) {
			this.selectionMode = FILES_ONLY;
			String pattern = names.stream().map(Pattern::quote).reduce(Pattern.quote(""), (p1, p2) -> p1 + "|" + p2);
			this.namePattern = Pattern.compile(pattern);
			return this;
		}

		public Builder selectingFoldersNotContaining(List<String> names) {
			this.selectionMode = FOLDERS_ONLY;
			this.excludeFolderContainingNames = names;
			return this;
		}

		public Builder excludingFolder(List<CloudFolderModel> cloudFolders) {
			this.excludeFolders = cloudFolders;
			return this;
		}

		public Builder selectingFolders() {
			this.selectionMode = FOLDERS_ONLY;
			return this;
		}

		public ChooseCloudNodeSettings build() {
			validate();
			return new ChooseCloudNodeSettings(this);
		}

		private void validate() {
			if (selectionMode == null) {
				throw new IllegalStateException("selectionMode is required");
			}
		}

	}

}
