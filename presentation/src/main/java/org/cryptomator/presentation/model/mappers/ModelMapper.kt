package org.cryptomator.presentation.model.mappers

abstract class ModelMapper<M, D> internal constructor() {

	fun fromModels(models: Iterable<M>): List<D> {
		return models.mapTo(ArrayList()) { fromModel(it) }
	}

	fun toModels(domainObjects: Iterable<D>): List<M> {
		return domainObjects.mapTo(ArrayList()) { toModel(it) }
	}

	abstract fun fromModel(model: M): D
	abstract fun toModel(domainObject: D): M

}
