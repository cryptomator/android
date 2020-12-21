package org.cryptomator.generator.utils;

import java.util.Optional;
import java.util.stream.Stream;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;

import static javax.lang.model.element.ElementKind.FIELD;
import static javax.lang.model.type.TypeKind.ARRAY;
import static javax.lang.model.type.TypeKind.NONE;

public class Type {

	private final TypeMirror mirror;
	private final Optional<TypeElement> element;
	private final Utils utils;

	public Utils getUtils() {
		return utils;
	}

	public Type(Utils utils, TypeMirror mirror) {
		if (utils == null) {
			throw new IllegalArgumentException("utils must not be null");
		}
		if (mirror == null) {
			throw new IllegalArgumentException("mirror must not be null");
		}
		this.utils = utils;
		this.mirror = mirror;
		if (mirror instanceof DeclaredType) {
			this.element = Optional.of((TypeElement) ((DeclaredType) mirror).asElement());
		} else {
			this.element = Optional.empty();
		}
	}

	public Type(Utils utils, TypeElement element) {
		if (utils == null) {
			throw new IllegalArgumentException("utils must not be null");
		}
		if (element == null) {
			throw new IllegalArgumentException("element must not be null");
		}
		this.utils = utils;
		this.mirror = utils.types.erasure(element.asType());
		this.element = Optional.of(element);
	}

	public TypeElement element() {
		return element.get();
	}

	public String qualifiedName() {

		return mirror.toString();
	}

	public String simpleName() {
		if (element.isPresent()) {
			return element.get().getSimpleName().toString();
		} else {
			return qualifiedName();
		}
	}

	public String packageName() {
		return utils.elements.getPackageOf(element()).getQualifiedName().toString();
	}

	public boolean isAssignableFrom(String other) {
		return new Type(utils, utils.elements.getTypeElement(other)).isAssignableTo(this);
	}

	public boolean isAssignableTo(String other) {
		return isAssignableTo(new Type(utils, utils.elements.getTypeElement(other)));
	}

	private boolean isAssignableTo(Type other) {
		return utils.types.isAssignable(mirror, other.mirror);
	}

	public Stream<Method> constructors() {
		return element //
				.map(type -> type.getEnclosedElements().stream() //
						.filter(ExecutableElement.class::isInstance) //
						.map(ExecutableElement.class::cast) //
						.filter(Method::isConstructor) //
						.map(executableElement -> new Method(utils, executableElement)))
				.orElse(Stream.empty()); //
	}

	public Stream<Method> methods() {
		return element //
				.map(type -> type.getEnclosedElements().stream() //
						.filter(ExecutableElement.class::isInstance) //
						.map(ExecutableElement.class::cast) //
						.filter(Method::isRegularMethod) //
						.map(executableElement -> new Method(utils, executableElement)))
				.orElse(Stream.empty()); //
	}

	public Stream<Field> fields() {
		return element //
				.map(type -> type.getEnclosedElements().stream() //
						.filter(VariableElement.class::isInstance) //
						.map(VariableElement.class::cast) //
						.filter(variable -> variable.getKind() == FIELD) //
						.map(variableElement -> new Field(utils, variableElement)))
				.orElse(Stream.empty()); //
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		if (obj == null || getClass() != obj.getClass())
			return false;
		return internalEquals((Type) obj);
	}

	private boolean internalEquals(Type o) {
		return utils.types.isSameType(mirror, o.mirror);
	}

	@Override
	public int hashCode() {
		return mirror.hashCode();
	}

	public Optional<Type> enclosingType() {
		if (mirror instanceof DeclaredType) {
			return Optional.ofNullable(((DeclaredType) mirror).getEnclosingType()) //
					.filter(mirror -> mirror.getKind() == NONE) //
					.map(mirror -> new Type(utils, mirror));
		} else {
			return Optional.empty();
		}
	}

	public boolean isClass() {
		return element.map(type -> type.getKind().isClass()).orElse(false);
	}

	private boolean isPrimitive() {
		return mirror.getKind().isPrimitive();
	}

	public boolean isPrimitiveWrapper() {
		return unboxed().isPresent();
	}

	public Optional<Type> primitiveType() {
		if (isPrimitive()) {
			return Optional.of(this);
		} else {
			return unboxed();
		}
	}

	private Optional<Type> unboxed() {
		try {
			return Optional.of(new Type(utils, utils.types.unboxedType(mirror)));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	public Optional<Type> boxed() {
		if (isPrimitive()) {
			return Optional.of(new Type(utils, utils.types.boxedClass((PrimitiveType) mirror)));
		} else {
			return Optional.empty();
		}
	}

	public boolean isArray() {
		return mirror.getKind() == ARRAY;
	}

}
