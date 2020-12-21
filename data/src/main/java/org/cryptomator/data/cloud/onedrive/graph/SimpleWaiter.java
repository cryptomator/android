package org.cryptomator.data.cloud.onedrive.graph;

// ------------------------------------------------------------------------------
// Copyright (c) 2015 Microsoft Corporation
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.
// ------------------------------------------------------------------------------

/**
 * A simple signal/waiter interface for synchronizing multi-threaded actions.
 */
public class SimpleWaiter {

	/**
	 * The internal lock object for this waiter.
	 */
	private final Object mInternalLock = new Object();

	/**
	 * Indicates if this waiter has been triggered.
	 */
	private boolean mTriggerState;

	/**
	 * BLOCKING: Waits for the signal to be triggered, or returns immediately if it has already been triggered.
	 */
	public void waitForSignal() {
		synchronized (mInternalLock) {
			if (this.mTriggerState) {
				return;
			}
			try {
				mInternalLock.wait();
			} catch (final InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
	}

	/**
	 * Triggers the signal for this waiter.
	 */
	public void signal() {
		synchronized (mInternalLock) {
			mTriggerState = true;
			mInternalLock.notifyAll();
		}
	}
}
