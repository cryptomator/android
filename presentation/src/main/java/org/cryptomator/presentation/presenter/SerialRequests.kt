package org.cryptomator.presentation.presenter

/**
 * Runs requests one at a time, e.g. through a use case, which allows only one run at a time. [run] gets a request and
 * a callback to call once it is done. Requests made while one runs are [merged][merge] into one, which runs next.
 */
class SerialRequests<T : Any>(private val merge: (waiting: T, next: T) -> T, private val run: (request: T, done: () -> Unit) -> Unit) {

	private var isRunning = false
	private var waiting: T? = null

	fun request(request: T) {
		if (isRunning) {
			waiting = waiting?.let { merge(it, request) } ?: request
			return
		}
		isRunning = true
		run(request) {
			isRunning = false
			waiting?.let {
				waiting = null
				request(it)
			}
		}
	}
}
