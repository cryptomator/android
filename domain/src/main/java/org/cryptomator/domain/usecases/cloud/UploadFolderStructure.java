package org.cryptomator.domain.usecases.cloud;

import java.util.ArrayList;
import java.util.List;

public class UploadFolderStructure {

	private final String folderName;
	private final List<UploadFile> files;
	private final List<UploadFolderStructure> subfolders;

	public UploadFolderStructure(String folderName) {
		this.folderName = folderName;
		this.files = new ArrayList<>();
		this.subfolders = new ArrayList<>();
	}

	public String getFolderName() {
		return folderName;
	}

	public List<UploadFile> getFiles() {
		return files;
	}

	public List<UploadFolderStructure> getSubfolders() {
		return subfolders;
	}

	public void addFile(UploadFile file) {
		this.files.add(file);
	}

	public void addSubfolder(UploadFolderStructure subfolder) {
		this.subfolders.add(subfolder);
	}

	public void setAllReplacing(boolean replacing) {
		for (UploadFile file : files) {
			file.setReplacing(replacing);
		}
		for (UploadFolderStructure subfolder : subfolders) {
			subfolder.setAllReplacing(replacing);
		}
	}

	public int totalFileCount() {
		int count = files.size();
		for (UploadFolderStructure subfolder : subfolders) {
			count += subfolder.totalFileCount();
		}
		return count;
	}
}
