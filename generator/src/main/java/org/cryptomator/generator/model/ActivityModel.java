package org.cryptomator.generator.model;

import org.cryptomator.generator.InjectIntent;
import org.cryptomator.generator.ProcessorException;
import org.cryptomator.generator.utils.Field;
import org.cryptomator.generator.utils.Type;

import java.util.List;
import java.util.Optional;

import javax.lang.model.element.TypeElement;

import static java.util.stream.Collectors.toList;

public class ActivityModel {

	private final String qualifiedName;

	private final boolean hasIntentField;
	private final String intentFieldName;
	private final String methodInIntentsName;

	private final boolean presenterHasIntentField;
	private final String presenterIntentFieldName;

	private final boolean hasPresenter;
	private final String presenterFieldName;
	private final String presenterQualifiedName;

	public ActivityModel(Type type) {
		try {
			this.qualifiedName = type.qualifiedName();
			java.util.Optional<Field> intentField = intentField(type);
			hasIntentField = intentField.isPresent();
			intentFieldName = intentField.map(Field::name).orElse(null);
			methodInIntentsName = intentField.map(ActivityModel::methodInIntentsName).orElse(null);
			Optional<Field> presenterField = presenterField(type);
			hasPresenter = presenterField.isPresent();
			presenterFieldName = presenterField.map(Field::name).orElse(null);
			presenterQualifiedName = presenterField.map(Field::type).map(Type::qualifiedName).orElse(null);
			java.util.Optional<Field> presenterIntentField = presenterIntentField(presenterField, intentField);
			presenterHasIntentField = presenterIntentField.isPresent();
			presenterIntentFieldName = presenterIntentField.map(Field::name).orElse(null);
		} catch (RuntimeException e) {
			throw new ProcessorException(e.getMessage(), type.element());
		}
	}

	private Optional<Field> presenterIntentField(Optional<Field> presenterField, Optional<Field> intentField) {
		if (!presenterField.isPresent() || !intentField.isPresent()) {
			return Optional.empty();
		}
		Type presenterType = presenterField.get().type();
		List<Field> intentFields = presenterType.fields().filter(field -> field.hasAnnotation(InjectIntent.class)).collect(toList());
		if (intentFields.size() > 1) {
			throw new ProcessorException("Only one field annotated with InjectIntent is allowed per Presenter", presenterType.element());
		} else if (intentFields.isEmpty()) {
			return java.util.Optional.empty();
		} else {
			Field presenterIntentField = intentFields.get(0);
			if (!presenterIntentField.type().qualifiedName().equals(intentField.get().type().qualifiedName())) {
				throw new ProcessorException("Intent field in presenter must have the same declaringType as intent field in activity", presenterIntentField.element());
			}
			return java.util.Optional.of(presenterIntentField);
		}
	}

	private static Optional<Field> presenterField(Type type) {
		return type //
				.fields() //
				.filter(field -> field.type().isAssignableTo("org.cryptomator.presentation.presenter.Presenter")) //
				.findFirst();
	}

	private static java.util.Optional<Field> intentField(Type type) {
		List<Field> intentFields = type.fields().filter(field -> field.hasAnnotation(InjectIntent.class)).collect(toList());
		if (intentFields.size() > 1) {
			throw new ProcessorException("Only one field annotated with InjectIntent is allowed per Activity", type.element());
		} else if (intentFields.isEmpty()) {
			return java.util.Optional.empty();
		} else {
			return java.util.Optional.of(intentFields.get(0));
		}
	}

	private static String methodInIntentsName(Field field) {
		Type fieldType = field.type();
		String name = fieldType.simpleName();
		return Character.toLowerCase(name.charAt(0)) + name.substring(1) + "From";
	}

	private static String qualifiedName(TypeElement type) {
		return type.getQualifiedName().toString();
	}

	public String getPresenterFieldName() {
		return presenterFieldName;
	}

	public String getPresenterQualifiedName() {
		return presenterQualifiedName;
	}

	public boolean isHasPresenter() {
		return hasPresenter;
	}

	public boolean isHasIntentField() {
		return hasIntentField;
	}

	public String getQualifiedName() {
		return qualifiedName;
	}

	public String getMethodInIntentsName() {
		return methodInIntentsName;
	}

	public String getIntentFieldName() {
		return intentFieldName;
	}

	public boolean isPresenterHasIntentField() {
		return presenterHasIntentField;
	}

	public String getPresenterIntentFieldName() {
		return presenterIntentFieldName;
	}
}
