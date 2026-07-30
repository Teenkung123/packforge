package com.teenkung.packforge.client.compat;

import com.mojang.blaze3d.platform.NativeImage;
import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.client.atlas.SpriteResize;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Optional ResourcePack Unbounded integration without a runtime dependency.
 *
 * <p>The reflected types are the stable loader-neutral API. Failures retain
 * PackForge's normal behavior and are logged once with enough context to diagnose
 * an incompatible API.</p>
 */
public final class ResourcePackUnboundedBridge {
	private static final String API_CLASS =
		"com.Teenkung.resourcepackunbounded.api.ResourcePackUnboundedApi";
	private static final String RESOURCE_KEY_CLASS =
		"com.Teenkung.resourcepackunbounded.api.ResourceKey";
	private static final String FALLBACK_SERVICE_CLASS =
		"com.Teenkung.resourcepackunbounded.api.FallbackResizeService";
	private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();
	private static final AtomicBoolean PROVIDER_REGISTERED = new AtomicBoolean();
	private static volatile Access access;
	private static volatile boolean lookupComplete;

	private ResourcePackUnboundedBridge() {}

	public static boolean configuredOwner(Identifier atlasTexture) {
		Access resolved = resolve();
		if (resolved == null) {
			return false;
		}
		try {
			if (!(boolean) resolved.available.invoke(null)) {
				return false;
			}
			registerFallbackProvider(resolved);
			Object key = resolved.resourceKeyConstructor.newInstance(
				atlasTexture.getNamespace(),
				atlasTexture.getPath()
			);
			Object query = resolved.ownership.invoke(null, key);
			return (boolean) resolved.configuredOwner.invoke(query);
		} catch (ReflectiveOperationException | RuntimeException failure) {
			logFailure(failure);
			return false;
		}
	}

	private static Access resolve() {
		if (lookupComplete) {
			return access;
		}
		synchronized (ResourcePackUnboundedBridge.class) {
			if (lookupComplete) {
				return access;
			}
			try {
				ClassLoader loader = ResourcePackUnboundedBridge.class.getClassLoader();
				Class<?> api = Class.forName(API_CLASS, false, loader);
				Class<?> resourceKey = Class.forName(RESOURCE_KEY_CLASS, false, loader);
				Method available = api.getMethod("available");
				Method ownership = api.getMethod("ownership", resourceKey);
				Class<?> fallbackService = Class.forName(FALLBACK_SERVICE_CLASS, false, loader);
				Method registerFallbackService = api.getMethod("registerFallbackService", fallbackService);
				Constructor<?> constructor = resourceKey.getConstructor(String.class, String.class);
				Class<?> query = ownership.getReturnType();
				Method configuredOwner = query.getMethod("configuredOwner");
				access = new Access(
					available,
					ownership,
					constructor,
					configuredOwner,
					fallbackService,
					registerFallbackService
				);
			} catch (ClassNotFoundException absent) {
				access = null;
			} catch (ReflectiveOperationException failure) {
				logFailure(failure);
				access = null;
			} finally {
				lookupComplete = true;
			}
			return access;
		}
	}

	public static void registerFallbackProviderIfAvailable() {
		Access resolved = resolve();
		if (resolved == null) {
			return;
		}
		try {
			if ((boolean) resolved.available.invoke(null)) {
				registerFallbackProvider(resolved);
			}
		} catch (ReflectiveOperationException | RuntimeException failure) {
			logFailure(failure);
		}
	}

	private static void registerFallbackProvider(Access resolved) throws ReflectiveOperationException {
		if (PROVIDER_REGISTERED.get()) {
			return;
		}
		Object provider = Proxy.newProxyInstance(
			ResourcePackUnboundedBridge.class.getClassLoader(),
			new Class<?>[]{resolved.fallbackService},
			new FallbackProviderHandler()
		);
		resolved.registerFallbackService.invoke(null, provider);
		PROVIDER_REGISTERED.set(true);
		PackForge.LOGGER.info("PackForge registered its animation-sheet scaler with ResourcePack Unbounded");
	}

	private static void logFailure(Throwable failure) {
		if (FAILURE_LOGGED.compareAndSet(false, true)) {
			PackForge.LOGGER.warn(
				"PackForge could not query the ResourcePack Unbounded ownership API; "
					+ "PackForge atlas guards remain active",
				failure
			);
		}
	}

	private record Access(
		Method available,
		Method ownership,
		Constructor<?> resourceKeyConstructor,
		Method configuredOwner,
		Class<?> fallbackService,
		Method registerFallbackService
	) {}

	private static final class FallbackProviderHandler implements InvocationHandler {
		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Exception {
			return switch (method.getName()) {
				case "providerId" -> "packforge";
				case "priority" -> 1_000;
				case "supports" -> supports(args[0]);
				case "resize" -> resize(args[0]);
				case "toString" -> "PackForgeFallbackResizeService";
				case "hashCode" -> System.identityHashCode(proxy);
				case "equals" -> proxy == args[0];
				default -> throw new UnsupportedOperationException(
					"Unsupported ResourcePack Unbounded provider method: " + method
				);
			};
		}

		private static boolean supports(Object request) throws ReflectiveOperationException {
			int divisor = (int) request.getClass().getMethod("scaleDivisor").invoke(request);
			return divisor >= 1;
		}

		private static Object resize(Object request) throws Exception {
			Class<?> requestType = request.getClass();
			int sourceWidth = integer(requestType, request, "sheetWidth");
			int sourceHeight = integer(requestType, request, "sheetHeight");
			int targetWidth = integer(requestType, request, "targetSheetWidth");
			int targetHeight = integer(requestType, request, "targetSheetHeight");
			int frameWidth = integer(requestType, request, "frameWidth");
			int frameHeight = integer(requestType, request, "frameHeight");
			int divisor = sourceWidth / targetWidth;
			int[] pixels = (int[]) requestType.getMethod("argbPixels").invoke(request);

			int[] scaledPixels;
			try (
				NativeImage source = new NativeImage(sourceWidth, sourceHeight, false)
			) {
				for (int y = 0; y < sourceHeight; y++) {
					for (int x = 0; x < sourceWidth; x++) {
						source.setPixel(x, y, pixels[x + y * sourceWidth]);
					}
				}
				try (NativeImage scaled = SpriteResize.resize(source, targetWidth, targetHeight)) {
					scaledPixels = scaled.getPixels();
				}
			}

			Object sprite = requestType.getMethod("sprite").invoke(request);
			@SuppressWarnings("unchecked")
			List<Object> animationFrames =
				(List<Object>) requestType.getMethod("animationFrames").invoke(request);
			boolean interpolated =
				(boolean) requestType.getMethod("interpolatedFrames").invoke(request);
			@SuppressWarnings("unchecked")
			Map<String, String> metadata =
				(Map<String, String>) requestType.getMethod("metadata").invoke(request);

			ClassLoader loader = requestType.getClassLoader();
			Class<?> resultType = Class.forName(
				"com.Teenkung.resourcepackunbounded.api.ResizeResult",
				true,
				loader
			);
			return resultType.getConstructors()[0].newInstance(
				sprite,
				targetWidth,
				targetHeight,
				frameWidth / divisor,
				frameHeight / divisor,
				scaledPixels,
				animationFrames,
				interpolated,
				metadata
			);
		}

		private static int integer(Class<?> type, Object instance, String method)
			throws ReflectiveOperationException {
			return (int) type.getMethod(method).invoke(instance);
		}
	}
}
