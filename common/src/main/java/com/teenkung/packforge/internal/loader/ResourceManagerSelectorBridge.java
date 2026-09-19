package com.teenkung.packforge.internal.loader;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Objects;

/**
 * Public-API method-handle adapter for the mc26.3 resource selector.
 *
 * <p>The selector type is intentionally discovered by its exact public class
 * name. Older mappings do not define it and therefore keep their ordinary
 * {@code Predicate} call path. The cached adapter retains only a method handle
 * and a stateless selector proxy; it does not retain a resource manager or any
 * resource-generation state.</p>
 */
public final class ResourceManagerSelectorBridge {
	private final MethodHandle listResources;
	private final Object selector;

	private ResourceManagerSelectorBridge(MethodHandle listResources, Object selector) {
		this.listResources = listResources;
		this.selector = selector;
	}

	/**
	 * Creates an adapter when the public selector interface exists.
	 *
	 * @param ownerClass the public resource-manager implementation class
	 * @param selectorClassName the exact nested selector binary name
	 * @param predicate a method handle matching the selector's single abstract method
	 * @return a cached-call adapter, or {@code null} when the selector type is absent
	 */
	public static ResourceManagerSelectorBridge discover(
		Class<?> ownerClass,
		String selectorClassName,
		MethodHandle predicate
	) {
		Objects.requireNonNull(ownerClass, "ownerClass");
		Objects.requireNonNull(selectorClassName, "selectorClassName");
		Objects.requireNonNull(predicate, "predicate");

		final Class<?> selectorClass;
		try {
			selectorClass = Class.forName(selectorClassName, false, ownerClass.getClassLoader());
		} catch (ClassNotFoundException absent) {
			return null;
		}
		if (!selectorClass.isInterface() || !Modifier.isPublic(selectorClass.getModifiers())) {
			throw new IllegalStateException("Resource selector is not a public interface: " + selectorClassName);
		}

		try {
			Object selector = MethodHandleProxies.asInterfaceInstance(selectorClass, predicate);
			MethodHandle listResources = MethodHandles.publicLookup().findVirtual(
				ownerClass,
				"listResources",
				MethodType.methodType(Map.class, String.class, selectorClass)
			);
			return new ResourceManagerSelectorBridge(listResources, selector);
		} catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException exception) {
			throw new IllegalStateException(
				"Resource selector exists but its public listResources ABI is unavailable",
				exception
			);
		}
	}

	/**
	 * Invokes the cached public selector overload.
	 *
	 * @param owner resource-manager instance
	 * @param path resource path prefix
	 * @return the resolved resource map
	 */
	public Map<?, ?> list(Object owner, String path) {
		try {
			return (Map<?, ?>) listResources.invoke(owner, path, selector);
		} catch (RuntimeException | Error failure) {
			throw failure;
		} catch (Throwable failure) {
			throw new IllegalStateException("Resource selector invocation failed", failure);
		}
	}
}
