package org.cryptomator.generator.templates;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;

import java.io.Writer;
import java.lang.reflect.Modifier;

import static org.apache.velocity.app.Velocity.mergeTemplate;
import static java.lang.Character.toLowerCase;
import static java.util.Arrays.stream;

abstract class Template<T> {

	static {
		try {
			Velocity.setProperty("resource.loader", "classpath");
			Velocity.setProperty("classpath.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
			Velocity.init();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private final String name;
	private final VelocityContext context = new VelocityContext();

	Template(T model) {
		this.name = "/templates/" + getClass().getSimpleName() + ".vm";
		stream(model.getClass().getDeclaredMethods()).filter(method -> Modifier.isPublic(method.getModifiers())).filter(method -> method.getParameterCount() == 0).forEach(method -> {
			try {
				this.context.put(methodToPropertyName(method.getName()), method.invoke(model));
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
	}

	private String methodToPropertyName(String method) {
		if (method.startsWith("get") || method.startsWith("set")) {
			method = method.substring(3);
		} else if (method.startsWith("is")) {
			method = method.substring(2);
		}
		return toLowerCase(method.charAt(0)) + method.substring(1);
	}

	public void render(Writer writer) {
		try {
			mergeTemplate(name, "UTF-8", context, writer);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
