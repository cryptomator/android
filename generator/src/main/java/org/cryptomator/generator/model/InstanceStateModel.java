package org.cryptomator.generator.model;

import org.cryptomator.generator.ProcessorException;
import org.cryptomator.generator.utils.Field;
import org.cryptomator.generator.utils.Type;

import java.util.Collection;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.lang.model.element.Element;

public class InstanceStateModel {

	private final String javaPackage;
	private final Map<Type, InstanceStateType> types = new TreeMap<>();

	public InstanceStateModel(String javaPackage) {
		this.javaPackage = javaPackage;
	}

	public void add(Field field) {
		Type type = field.declaringType();
		if (!types.containsKey(type)) {
			types.put(type, new InstanceStateType(type));
		}
		InstanceStateField instanceStateField = new InstanceStateField(field);
		types.get(type).add(instanceStateField);
	}

	public Collection<InstanceStateType> getTypes() {
		return types.values();
	}

	public Element[] elements() {
		return types.values().stream() //
				.flatMap(type -> type.fields.stream()) //
				.map(InstanceStateField::element) //
				.toArray(Element[]::new);
	}

	public String getJavaPackage() {
		return javaPackage;
	}

	public static class InstanceStateType {

		private final SortedSet<InstanceStateField> fields = new TreeSet<>();
		private final String qualifiedName;

		public InstanceStateType(Type type) {
			this.qualifiedName = type.qualifiedName();
		}

		public void add(InstanceStateField field) {
			fields.add(field);
		}

		public String getQualifiedName() {
			return qualifiedName;
		}

		public SortedSet<InstanceStateField> getFields() {
			return fields;
		}
	}

	public static class InstanceStateField implements Comparable<InstanceStateField> {

		private static int nextBundleKey = 0;

		private final Field field;
		private final String name;
		private final String qualifiedType;
		private final String putMethod;
		private final String getMethod;
		private final String bundleKey;
		private final boolean castRequired;

		public InstanceStateField(Field field) {
			this.field = field;
			if (field.isPrivate()) {
				throw new ProcessorException("Field annotated with @InstanceState must not be private", field.element());
			}
			this.qualifiedType = field.type().qualifiedName();
			this.name = field.name();
			this.putMethod = determinePutMethod(field.type());
			this.getMethod = determineGetMethod(field.type());
			this.bundleKey = "InstanceState$" + nextBundleKey++;
			this.castRequired = "getSerializable".equals(getMethod);
		}

		private String determinePutMethod(Type type) {
			return determineMethod(type, "put");
		}

		private String determineGetMethod(Type type) {
			return determineMethod(type, "get");
		}

		private String determineMethod(Type type, String prefix) {
			if (type.primitiveType().isPresent()) {
				String name = type.primitiveType().get().simpleName();
				return prefix + Character.toUpperCase(name.charAt(0)) + name.substring(1);
			} else if (type.isAssignableTo("java.lang.String")) {
				return prefix + "String";
			} else if (type.isAssignableTo("java.io.Serializable")) {
				return prefix + "Serializable";
			} else if (type.isAssignableTo("android.os.Parcelable")) {
				return prefix + "Parcelable";
			}
			throw new ProcessorException("Unsupported type. InstanceState must be a primitive type, String or Serializable", field.element());
		}

		public String getGetMethod() {
			return getMethod;
		}

		public boolean isCastRequired() {
			return castRequired;
		}

		public String getName() {
			return name;
		}

		public String getQualifiedType() {
			return qualifiedType;
		}

		public String getPutMethod() {
			return putMethod;
		}

		public String getBundleKey() {
			return bundleKey;
		}

		public Element element() {
			return field.element();
		}

		@Override
		public int compareTo(InstanceStateField instanceStateField) {
			return this.bundleKey.compareTo(instanceStateField.bundleKey);
		}
	}

}
