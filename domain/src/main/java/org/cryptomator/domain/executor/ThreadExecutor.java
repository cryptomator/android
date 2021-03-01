package org.cryptomator.domain.executor;

import java.util.concurrent.Executor;

/**
 * Executor implementation can be based on different frameworks or techniques of asynchronous
 * execution, but every implementation will execute a use case out of the UI thread.
 */
public interface ThreadExecutor extends Executor {

}
