package org.cryptomator.generator.model;

import org.cryptomator.generator.Intent;
import org.cryptomator.generator.Optional;

import java.util.Comparator;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

public class IntentReaderModel implements Comparable<IntentReaderModel> {

	private final String javaPackage;
	private final String className;
	private final String targetActivity;
	private final String intentInterface;
	private final String readMethodName;
	private final SortedSet<ParameterModel> parameters;

	public IntentReaderModel(TypeElement type) {
		this.intentInterface = type.getQualifiedName().toString();
		this.readMethodName = readMethodName(type);
		this.javaPackage = javaPackage(type);
		this.className = className(type);
		this.targetActivity = targetActivity(type);
		this.parameters = parameters(type);
	}

	private static String javaPackage(TypeElement type) {
		String qualifiedName = type.getQualifiedName().toString();
		int lastDot = qualifiedName.lastIndexOf('.');
		return qualifiedName.substring(0, lastDot);
	}

	private static String className(TypeElement type) {
		String qualifiedName = type.getQualifiedName().toString();
		int lastDot = qualifiedName.lastIndexOf('.');
		return qualifiedName.substring(lastDot + 1) + "Reader";
	}

	private static String targetActivity(TypeElement type) {
		return type //
				.getAnnotationMirrors() //
				.stream() //
				.filter(is(Intent.class)) //
				.findFirst().get().getElementValues().entrySet() //
				.stream() //
				.map(entry -> (Map.Entry<ExecutableElement, AnnotationValue>) entry) //
				.filter(entry -> "value".equals(entry.getKey().getSimpleName().toString())) //
				.map(Map.Entry::getValue) //
				.map(AnnotationValue::getValue) //
				.map(DeclaredType.class::cast) //
				.map(DeclaredType::asElement) //
				.map(TypeElement.class::cast) //
				.findFirst().get() //
				.getQualifiedName() //
				.toString();
	}

	private static Predicate<AnnotationMirror> is(Class<?> type) {
		return mirror -> {
			TypeElement typeElement = (TypeElement) mirror.getAnnotationType().asElement();
			String qualifiedName = typeElement.getQualifiedName().toString();
			return qualifiedName.equals(type.getName());
		};
	}

	private static SortedSet<ParameterModel> parameters(TypeElement type) {
		return type //
				.getEnclosedElements() //
				.stream() //
				.filter(ExecutableElement.class::isInstance) //
				.map(ExecutableElement.class::cast) //
				.map(ParameterModel::new) //
				.collect(Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(ParameterModel::getName))));
	}

	private static String readMethodName(TypeElement type) {
		String qualifiedName = type.getQualifiedName().toString();
		int lastDot = qualifiedName.lastIndexOf('.');
		String name = qualifiedName.substring(lastDot + 1);
		return Character.toLowerCase(name.charAt(0)) + name.substring(1) + "From";
	}

	public String getJavaPackage() {
		return javaPackage;
	}

	public String getIntentInterface() {
		return intentInterface;
	}

	public String getClassName() {
		return className;
	}

	public Iterable<ParameterModel> getParameters() {
		return parameters;
	}

	public String getTargetActivity() {
		return targetActivity;
	}

	public String getReadMethodName() {
		return readMethodName;
	}

	@Override
	public int compareTo(IntentReaderModel intentReaderModel) {
		return (this.javaPackage + this.className).compareTo(intentReaderModel.javaPackage + intentReaderModel.className);
	}

	public static class ParameterModel {

		private final String name;
		private final String type;
		private final boolean required;

		private ParameterModel(ExecutableElement element) {
			this.name = element.getSimpleName().toString();
			this.type = type(element);
			this.required = element.getAnnotation(Optional.class) == null;
		}

		private static String type(ExecutableElement element) {
			DeclaredType declaredType = (DeclaredType) element.getReturnType();
			TypeElement type = (TypeElement) declaredType.asElement();
			return type.getQualifiedName().toString();
		}

		public String getName() {
			return name;
		}

		public String getType() {
			return type;
		}

		public boolean isRequired() {
			return required;
		}

	}
}
