package org.cryptomator.data.cloud.onedrive.graph;

import com.microsoft.graph.core.GraphErrorCodes;

/**
 * An exception from the client.
 */
public class ClientException extends com.microsoft.graph.core.ClientException {

	private static final long serialVersionUID = -10662352567392559L;

	private final Enum<GraphErrorCodes> errorCode;

	/**
	 * Creates the client exception
	 *
	 * @param message the message to display
	 * @param ex      the exception from
	 */
	public ClientException(final String message, final Throwable ex, Enum<GraphErrorCodes> errorCode) {
		super(message, ex);

		this.errorCode = errorCode;
	}

	public Enum<GraphErrorCodes> errorCode() {
		return errorCode;
	}
}
