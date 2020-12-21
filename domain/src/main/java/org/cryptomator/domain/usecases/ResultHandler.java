package org.cryptomator.domain.usecases;

/**
 * A handler for use case results.
 * 
 * @param <T> The type of result this handler can handle.
 */
public interface ResultHandler<T> {

	/**
	 * Invoked after successful execution of a use case.
	 * 
	 * @param result the use case result
	 */
	void onSuccess(T result);

	/**
	 * Invoked after failed execution of a use case.
	 * 
	 * @param e the error that occured
	 */
	void onError(Throwable e);

	/**
	 * <p>
	 * Invoked after successful and failed execution of a use case.
	 * <p>
	 * This method is always invoked after onSuccess / onError.
	 */
	void onFinished();

}
