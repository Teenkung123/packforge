package com.teenkung.packforge.client.model;

import com.mojang.datafixers.util.Pair;
import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.config.FeatureFlags;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.Util;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/** Reload-scoped, ordering-preserving replacement for vanilla model parsing. */
public final class ModelParseOptimizer {
	private static final FileToIdConverter MODEL_LISTER = FileToIdConverter.json("models");
	private static final ConcurrentHashMap<ContentKey, String> DUPLICATE_JSON = new ConcurrentHashMap<>();

	public static boolean enabled() {
		return FeatureFlags.modelParseBatchingEnabled()
			|| FeatureFlags.modelParseTimingEnabled()
			|| FeatureFlags.modelAdaptiveBatchingEnabled()
			|| FeatureFlags.modelDuplicateParseCacheEnabled();
	}

	public static CompletableFuture<Map<ResourceLocation, BlockModel>> load(ResourceManager manager, Executor executor) {
		return CompletableFuture.supplyAsync(() -> {
			long startNs = ModelParseTimings.start();
			Map<ResourceLocation, Resource> resources = MODEL_LISTER.listMatchingResources(manager);
			ModelParseTimings.recordList(startNs);
			return List.copyOf(resources.entrySet());
		}, executor).thenCompose(entries -> {
			if (!FeatureFlags.modelParseBatchingEnabled()) {
				return CompletableFuture.supplyAsync(() -> collect(parse(entries)), executor);
			}
			List<CompletableFuture<List<Pair<ResourceLocation, BlockModel>>>> jobs = new ArrayList<>();
			for (ModelBatchPlan.Range range : ModelBatchPlan.create(entries.size(), FeatureFlags.modelParseBatchSize(), FeatureFlags.modelAdaptiveBatchingEnabled())) {
				jobs.add(CompletableFuture.supplyAsync(() -> parse(entries.subList(range.fromInclusive(), range.toExclusive())), executor));
			}
			return Util.sequence(jobs).thenApply(batches -> collect(batches.stream().flatMap(List::stream).toList()));
		}).whenComplete((ignored, error) -> ModelParseTimings.log());
	}

	public static void resetForReload() { DUPLICATE_JSON.clear(); ModelParseTimings.reset(); }

	private static List<Pair<ResourceLocation, BlockModel>> parse(List<Map.Entry<ResourceLocation, Resource>> entries) {
		ModelParseTimings.recordBatch();
		List<Pair<ResourceLocation, BlockModel>> result = new ArrayList<>(entries.size());
		for (Map.Entry<ResourceLocation, Resource> entry : entries) {
			try {
				result.add(Pair.of(MODEL_LISTER.fileToId(entry.getKey()), parseOne(entry.getValue())));
			} catch (Exception error) {
				PackForge.LOGGER.error("Failed to load model {}", entry.getKey(), error);
			}
		}
		return result;
	}

	private static BlockModel parseOne(Resource resource) throws Exception {
		if (!FeatureFlags.modelDuplicateParseCacheEnabled() && !FeatureFlags.modelParseTimingEnabled()) {
			try (BufferedReader reader = resource.openAsReader()) {
				return BlockModel.fromStream(reader);
			}
		}
		long readStartNs = ModelParseTimings.start();
		byte[] bytes;
		try (var stream = resource.open()) {
			bytes = stream.readAllBytes();
		}
		ModelParseTimings.recordRead(readStartNs);
		String json = FeatureFlags.modelDuplicateParseCacheEnabled()
			? DUPLICATE_JSON.computeIfAbsent(new ContentKey(bytes), ignored -> new String(bytes, StandardCharsets.UTF_8))
			: new String(bytes, StandardCharsets.UTF_8);
		long parseStartNs = ModelParseTimings.start();
		try (StringReader reader = new StringReader(json)) {
			BlockModel model = BlockModel.fromStream(reader);
			ModelParseTimings.recordParse(parseStartNs);
			return model;
		}
	}

	private static Map<ResourceLocation, BlockModel> collect(List<Pair<ResourceLocation, BlockModel>> models) {
		long collectStartNs = ModelParseTimings.start();
		LinkedHashMap<ResourceLocation, BlockModel> result = new LinkedHashMap<>();
		for (Pair<ResourceLocation, BlockModel> model : models) {
			result.putIfAbsent(model.getFirst(), model.getSecond());
		}
		ModelParseTimings.recordCollect(collectStartNs);
		return result;
	}

	private static final class ContentKey {
		private final byte[] bytes;
		private final int hash;

		ContentKey(byte[] source) {
			this.bytes = source.clone();
			this.hash = Arrays.hashCode(this.bytes);
		}

		@Override
		public int hashCode() {
			return this.hash;
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof ContentKey key && Arrays.equals(this.bytes, key.bytes);
		}
	}

	private ModelParseOptimizer() {}
}
