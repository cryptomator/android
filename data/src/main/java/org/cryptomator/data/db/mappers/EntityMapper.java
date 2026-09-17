package org.cryptomator.data.db.mappers;

import org.cryptomator.domain.exception.BackendException;

import java.util.ArrayList;
import java.util.List;

public abstract class EntityMapper<E, D> {

	EntityMapper() {
	}

	public List<D> fromEntities(Iterable<E> entities) throws BackendException {
		List<D> result = new ArrayList<>();
		for (E entity : entities) {
			result.add(fromEntity(entity));
		}
		return result;
	}

	protected abstract D fromEntity(E entity) throws BackendException;

	protected abstract E toEntity(D domainObject);

}
