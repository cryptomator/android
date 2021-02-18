package org.cryptomator.presentation.model.mappers

import org.cryptomator.domain.usecases.cloud.Progress
import org.cryptomator.presentation.model.ProgressModel
import javax.inject.Inject

class ProgressModelMapper @Inject internal constructor(private val progressStateModelMapper: ProgressStateModelMapper) : ModelMapper<ProgressModel, Progress<*>>() {

	/**
	 * @throws IllegalStateException
	 */
	@Deprecated("Not implemented")
	override fun fromModel(model: ProgressModel): Progress<*> {
		throw IllegalStateException("Not implemented")
	}

	override fun toModel(domainObject: Progress<*>): ProgressModel {
		return ProgressModel(progressStateModelMapper.toModel(domainObject.state()), domainObject.asPercentage())
	}
}
