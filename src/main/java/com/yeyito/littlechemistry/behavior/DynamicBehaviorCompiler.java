package com.yeyito.littlechemistry.behavior;

import org.codehaus.commons.compiler.CompileException;
import org.codehaus.janino.SimpleCompiler;

import java.lang.reflect.Constructor;

public final class DynamicBehaviorCompiler {
	public static final String GENERATED_CLASS_NAME = "GeneratedBehaviorImpl";

	private DynamicBehaviorCompiler() {
	}

	public static Compiled compile(String source) {
		String normalized = normalize(source);
		try {
			SimpleCompiler compiler = new SimpleCompiler();
			compiler.setParentClassLoader(DynamicBehavior.class.getClassLoader());
			compiler.cook(normalized);
			Class<?> generated = Class.forName(GENERATED_CLASS_NAME, false, compiler.getClassLoader());
			if (!DynamicBehavior.class.isAssignableFrom(generated)) {
				throw new IllegalArgumentException(GENERATED_CLASS_NAME + " must implement DynamicBehavior");
			}
			@SuppressWarnings("unchecked")
			Class<? extends DynamicBehavior> behaviorClass = (Class<? extends DynamicBehavior>) generated;
			Constructor<? extends DynamicBehavior> constructor = behaviorClass.getConstructor();
			return new Compiled(normalized, constructor, compiler.getClassLoader());
		} catch (CompileException error) {
			throw new IllegalArgumentException("Java compilation failed: " + safeMessage(error), error);
		} catch (ReflectiveOperationException | LinkageError error) {
			throw new IllegalArgumentException("Generated behavior class is invalid: " + safeMessage(error), error);
		}
	}

	private static String normalize(String source) {
		if (source == null || source.isBlank()) {
			throw new IllegalArgumentException("Behavior source is empty");
		}
		String normalized = source.strip();
		if (normalized.length() > 65_536 || normalized.indexOf('\0') >= 0) {
			throw new IllegalArgumentException("Behavior source must be valid text no longer than 65,536 characters");
		}
		return normalized;
	}

	private static String safeMessage(Throwable error) {
		String message = error.getMessage();
		if (message == null || message.isBlank()) return error.getClass().getSimpleName();
		String oneLine = message.replaceAll("[\\r\\n]+", " ").trim();
		return oneLine.length() <= 2_000 ? oneLine : oneLine.substring(0, 2_000) + "…";
	}

	public record Compiled(
			String source,
			Constructor<? extends DynamicBehavior> constructor,
			ClassLoader classLoader
	) {
		public DynamicBehavior instantiate() {
			try {
				return constructor.newInstance();
			} catch (ReflectiveOperationException | LinkageError error) {
				throw new IllegalArgumentException("Could not instantiate generated behavior: " + safeMessage(error), error);
			}
		}
	}
}
