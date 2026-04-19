package org.cryptomator.presentation.service

interface RestoreOutcomeHandler {
	/**
 * Handles the outcome of a restore operation.
 *
 * @param outcome The result of a restore operation containing status and related details. 
 */
fun onRestoreOutcome(outcome: RestoreOutcome)
}
