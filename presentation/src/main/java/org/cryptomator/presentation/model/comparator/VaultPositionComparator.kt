package org.cryptomator.presentation.model.comparator

import org.cryptomator.presentation.model.VaultModel

class VaultPositionComparator : Comparator<VaultModel> {

	override fun compare(v1: VaultModel, v2: VaultModel): Int {
		return v1.position - v2.position
	}
}
