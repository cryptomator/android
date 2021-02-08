package org.cryptomator.generator.model;

import org.cryptomator.generator.Intent;
import org.cryptomator.generator.Optional;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

import static java.lang.Character.toLowerCase;

public class IntentBuilderModel implements Comparable<IntentBuilderModel> {

	private final String javaPackage;
	private final String className;
	private final String targetActivity;
	private final String targetActivitySimpleName;
	private final String buildMethodName;
	private final Set<ParameterModel> parameters;

	public IntentBuilderModel(TypeElement type) {
		this.javaPackage = javaPackage(type);
		this.className = className(type);
		this.targetActivity = targetActivity(type);
		this.targetActivitySimpleName = targetActivitySimpleName(targetActivity);
		this.parameters = parameters(type);
		this.buildMethodName = buildMethodName(type);
	}

	private static String javaPackage(TypeElement type) {
		String qualifiedName = type.getQualifiedName().toString();
		int lastDot = qualifiedName.lastIndexOf('.');
		return qualifiedName.substring(0, lastDot);
	}

	private static String className(TypeElement type) {
		String qualifiedName = type.getQualifiedName().toString();
		int lastDot = qualifiedName.lastIndexOf('.');
		return qualifiedName.substring(lastDot + 1) + "Builder";
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
				.getQualifiedName().toString();
	}

	private static Predicate<AnnotationMirror> is(Class<?> type) {
		return mirror -> {
			TypeElement typeElement = (TypeElement) mirror.getAnnotationType().asElement();
			String qualifiedName = typeElement.getQualifiedName().toString();
			return qualifiedName.equals(type.getName());
		};
	}

	private static String targetActivitySimpleName(String targetActivity) {
		int lastDot = targetActivity.lastIndexOf('.');
		String name = targetActivity.substring(lastDot + 1);
		return toLowerCase(name.charAt(0)) + name.substring(1);
	}

	private static Set<ParameterModel> parameters(TypeElement type) {
		return type //
				.getEnclosedElements() //
				.stream() //
				.filter(ExecutableElement.class::isInstance) //
				.map(ExecutableElement.class::cast) //
				.map(ParameterModel::new) //
				.collect(Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(ParameterModel::getName))));
	}

	private static String buildMethodName(TypeElement type) {
		String qualifiedName = type.getQualifiedName().toString();
		int lastDot = qualifiedName.lastIndexOf('.');
		String name = qualifiedName.substring(lastDot + 1);
		return toLowerCase(name.charAt(0)) + name.substring(1);
	}

	public String getJavaPackage() {
		return javaPackage;
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

	public String getTargetActivitySimpleName() {
		return targetActivitySimpleName;
	}

	public String getBuildMethodName() {
		return buildMethodName;
	}

	@Override
	public int compareTo(IntentBuilderModel intentBuilderModel) {
		return (this.javaPackage + this.className).compareTo(intentBuilderModel.javaPackage + intentBuilderModel.className);
	}

	public static class ParameterModel {

		private final String nameWithFirstCharUppercase;
		private final String name;
		private final String type;
		private final boolean required;

		private ParameterModel(ExecutableElement element) {
			this.name = element.getSimpleName().toString();
			this.nameWithFirstCharUppercase = Character.toUpperCase(name.charAt(0)) + name.substring(1);
			this.type = type(element);
			this.required = element.getAnnotation(Optional.class) == null;
		}

		private static String type(ExecutableElement element) {
			DeclaredType declaredType = (DeclaredType) element.getReturnType();
			TypeElement type = (TypeElement) declaredType.asElement();
			return type.getQualifiedName().toString();
		}

		public String getNameWithFirstCharUppercase() {
			return nameWithFirstCharUppercase;
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
