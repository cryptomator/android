package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.CloudFile;

public interface FileTransferState extends ProgressState {

	CloudFile file();

}
