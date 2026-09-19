package com.teenkung.packforge.internal.loader;

import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ResourceManagerSelectorBridgeTest {
	@Test
	void absentSelectorLeavesLegacyPredicatePathAvailable() {
		LegacyManager manager = new LegacyManager();
		Predicate<String> predicate = ResourceManagerSelectorBridgeTest::isFixture;

		assertNull(discover("missing.ResourceManager$Selector"));
		assertEquals(Map.of("textures/example/a", "a"), manager.listResources("textures", predicate));
	}

	@Test
	void publicSelectorMethodHandleInvokesSelectorOverload() throws Throwable {
		ResourceManagerSelectorBridge bridge = discover(PublicSelector.class.getName());
		if (bridge == null) {
			throw new AssertionError("test selector interface was not discovered");
		}

		Map<?, ?> resources = bridge.list(new SelectorManager(), "textures");
		assertEquals(Map.of("textures/example/a", "a"), resources);
	}

	private static ResourceManagerSelectorBridge discover(String selectorClassName) {
		try {
			MethodHandle predicate = MethodHandles.lookup().findStatic(
				ResourceManagerSelectorBridgeTest.class,
				"isFixture",
				MethodType.methodType(boolean.class, String.class)
			);
			return ResourceManagerSelectorBridge.discover(SelectorManager.class, selectorClassName, predicate);
		} catch (NoSuchMethodException | IllegalAccessException exception) {
			throw new AssertionError(exception);
		}
	}

	private static boolean isFixture(String value) {
		return value.startsWith("textures/example/");
	}

	public interface PublicSelector {
		boolean isIncluded(String value);
	}

	public static final class SelectorManager {
		public Map<String, String> listResources(String path, PublicSelector selector) {
			return selector.isIncluded(path + "/example/a")
				? Map.of(path + "/example/a", "a")
				: Map.of();
		}
	}

	private static final class LegacyManager {
		private Map<String, String> listResources(String path, Predicate<String> predicate) {
			return predicate.test(path + "/example/a")
				? Map.of(path + "/example/a", "a")
				: Map.of();
		}
	}
}
