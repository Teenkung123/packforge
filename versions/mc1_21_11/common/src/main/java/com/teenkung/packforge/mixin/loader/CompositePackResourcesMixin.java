package com.teenkung.packforge.mixin.loader;

import com.teenkung.packforge.config.FeatureFlags;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.CompositePackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(CompositePackResources.class)
public abstract class CompositePackResourcesMixin {
	@Shadow @Final private List<PackResources> packResourcesStack;

	@Unique
	private final Map<PackType, Set<String>> packforge$namespaces = new ConcurrentHashMap<>();

	@Unique
	private final Map<PackType, Map<PackResources, Set<String>>> packforge$namespacesByPack = new ConcurrentHashMap<>();

	@Unique
	private Set<String> packforge$namespacesFor(PackResources pack, PackType type) {
		Map<PackResources, Set<String>> byPack = this.packforge$namespacesByPack.computeIfAbsent(
			type,
			ignored -> new IdentityHashMap<>()
		);
		synchronized (byPack) {
			Set<String> cached = byPack.get(pack);
			if (cached != null) {
				return cached;
			}
			Set<String> namespaces = pack.getNamespaces(type);
			byPack.put(pack, namespaces);
			return namespaces;
		}
	}

	@Inject(method = "getResource", at = @At("HEAD"), cancellable = true)
	private void packforge$fastGetResource(
		PackType type,
		Identifier location,
		CallbackInfoReturnable<IoSupplier<InputStream>> cir
	) {
		if (!FeatureFlags.loaderIndexEnabled()) {
			return;
		}
		for (PackResources pack : this.packResourcesStack) {
			if (!this.packforge$namespacesFor(pack, type).contains(location.getNamespace())) {
				continue;
			}
			IoSupplier<InputStream> resource = pack.getResource(type, location);
			if (resource != null) {
				cir.setReturnValue(resource);
				return;
			}
		}
		cir.setReturnValue(null);
	}

	@Inject(method = "listResources", at = @At("HEAD"), cancellable = true)
	private void packforge$fastListResources(
		PackType type,
		String namespace,
		String directory,
		PackResources.ResourceOutput output,
		CallbackInfo ci
	) {
		if (!FeatureFlags.loaderIndexEnabled()) {
			return;
		}
		Map<Identifier, IoSupplier<InputStream>> resources = new HashMap<>();
		for (PackResources pack : this.packResourcesStack) {
			if (!this.packforge$namespacesFor(pack, type).contains(namespace)) {
				continue;
			}
			pack.listResources(type, namespace, directory, resources::putIfAbsent);
		}
		resources.forEach(output);
		ci.cancel();
	}

	@Inject(method = "getNamespaces", at = @At("HEAD"), cancellable = true)
	private void packforge$fastGetNamespaces(PackType type, CallbackInfoReturnable<Set<String>> cir) {
		if (!FeatureFlags.loaderIndexEnabled()) {
			return;
		}
		cir.setReturnValue(this.packforge$namespaces.computeIfAbsent(type, ignored -> {
			Set<String> namespaces = new HashSet<>();
			for (PackResources pack : this.packResourcesStack) {
				namespaces.addAll(this.packforge$namespacesFor(pack, type));
			}
			return Set.copyOf(namespaces);
		}));
	}

	@Inject(method = "close", at = @At("HEAD"))
	private void packforge$clearInstanceCaches(CallbackInfo ci) {
		this.packforge$namespaces.clear();
		this.packforge$namespacesByPack.clear();
	}
}
