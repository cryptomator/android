package org.cryptomator.presentation.docprovider

import android.os.ParcelFileDescriptor

class SafeReliablePipe(pipeDescriptors: Array<ParcelFileDescriptor>) {

	val reader: ParcelFileDescriptor = pipeDescriptors[0]
	val writer: ParcelFileDescriptor = pipeDescriptors[1]

	companion object {

		fun createSafeReliablePipe(): SafeReliablePipe {
			return SafeReliablePipe(ParcelFileDescriptor.createReliablePipe())
		}
	}
}