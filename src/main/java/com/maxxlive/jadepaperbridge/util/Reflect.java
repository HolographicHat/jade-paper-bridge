package com.maxxlive.jadepaperbridge.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Access to the few members Jade opens with its class tweaker that are still private on Paper.
 */
public final class Reflect {
	private Reflect() {
	}

	public static Field field(Class<?> owner, String name) {
		try {
			Field field = owner.getDeclaredField(name);
			field.setAccessible(true);
			return field;
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Missing field %s.%s".formatted(owner.getName(), name), e);
		}
	}

	public static Method method(Class<?> owner, String name, Class<?>... parameterTypes) {
		try {
			Method method = owner.getDeclaredMethod(name, parameterTypes);
			method.setAccessible(true);
			return method;
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Missing method %s.%s".formatted(owner.getName(), name), e);
		}
	}

	@SuppressWarnings("unchecked")
	public static <T> T get(Field field, Object instance) {
		try {
			return (T) field.get(instance);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public static <T> T invoke(Method method, Object instance, Object... args) {
		try {
			return (T) method.invoke(instance, args);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
