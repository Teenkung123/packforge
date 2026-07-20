package com.teenkung.packforge.client.model;

import com.mojang.datafixers.util.Pair;
import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.config.FeatureFlags;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.client.resources.model.cuboid.CuboidModel;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

public final class ModelParseOptimizer {
	private static final FileToIdConverter MODEL_LISTER = FileToIdConverter.json("models");
	private static final ConcurrentHashMap<ContentKey, UnbakedModel> DUPLICATE_CACHE = new ConcurrentHashMap<>();

	public static CompletableFuture<Map<Identifier, UnbakedModel>> loadBlockModels(ResourceManager manager, Executor executor) {
		if (!FeatureFlags.modelParseBatchingEnabled()) {
			return CompletableFuture.supplyAsync(() -> loadSerial(manager), executor).whenComplete((ignored, error) -> ModelParseTimings.log());
		}
		return CompletableFuture.supplyAsync(() -> {
			long startNs = ModelParseTimings.start();
			Map<Identifier, Resource> resources = MODEL_LISTER.listMatchingResources(manager);
			ModelParseTimings.recordList(startNs);
			return resources;
		}, executor).thenCompose(resources -> {
			List<Map.Entry<Identifier, Resource>> entries = List.copyOf(resources.entrySet());
			int batchSize = modelBatchSize(entries.size());
			ArrayList<CompletableFuture<List<Pair<Identifier, UnbakedModel>>>> jobs = new ArrayList<>();
			for (int from = 0; from < entries.size(); from += batchSize) {
				int start = from;
				int end = Math.min(entries.size(), from + batchSize);
				jobs.add(CompletableFuture.supplyAsync(() -> parseBatch(entries.subList(start, end)), executor));
			}
			return Util.sequence(jobs).thenApply(batches -> {
				long collectStartNs = ModelParseTimings.start();
				Map<Identifier, UnbakedModel> result = batches.stream()
					.flatMap(List::stream)
					.filter(Objects::nonNull)
					.collect(Collectors.toUnmodifiableMap(Pair::getFirst, Pair::getSecond));
				ModelParseTimings.recordCollect(collectStartNs);
				return result;
			});
		}).whenComplete((ignored, error) -> ModelParseTimings.log());
	}

	public static void resetForReload() {
		DUPLICATE_CACHE.clear();
		ModelParseTimings.reset();
	}

	private static Map<Identifier, UnbakedModel> loadSerial(ResourceManager manager) {
		long listStartNs = ModelParseTimings.start();
		Map<Identifier, Resource> resources = MODEL_LISTER.listMatchingResources(manager);
		ModelParseTimings.recordList(listStartNs);
		long collectStartNs = ModelParseTimings.start();
		Map<Identifier, UnbakedModel> result = resources.entrySet().stream()
			.map(ModelParseOptimizer::parseOne)
			.filter(Objects::nonNull)
			.collect(Collectors.toUnmodifiableMap(Pair::getFirst, Pair::getSecond));
		ModelParseTimings.recordCollect(collectStartNs);
		return result;
	}

	private static List<Pair<Identifier, UnbakedModel>> parseBatch(List<Map.Entry<Identifier, Resource>> entries) {
		ModelParseTimings.recordBatch();
		ArrayList<Pair<Identifier, UnbakedModel>> result = new ArrayList<>(entries.size());
		for (Map.Entry<Identifier, Resource> entry : entries) {
			Pair<Identifier, UnbakedModel> parsed = parseOne(entry);
			if (parsed != null) {
				result.add(parsed);
			}
		}
		return result;
	}

	private static @Nullable Pair<Identifier, UnbakedModel> parseOne(Map.Entry<Identifier, Resource> entry) {
		Identifier modelId = MODEL_LISTER.fileToId(entry.getKey());
		Resource resource = entry.getValue();
		try {
			UnbakedModel model;
			if (FeatureFlags.modelDuplicateParseCacheEnabled() || FeatureFlags.modelParseTimingEnabled()) {
				byte[] bytes;
				long readStartNs = ModelParseTimings.start();
				try (var stream = resource.open()) {
					bytes = stream.readAllBytes();
				}
				ModelParseTimings.recordRead(readStartNs);
				if (FeatureFlags.modelDuplicateParseCacheEnabled()) {
					ContentKey key = new ContentKey(bytes);
					model = DUPLICATE_CACHE.computeIfAbsent(key, ignored -> parseString(new String(bytes, StandardCharsets.UTF_8)));
				} else {
					model = parseString(new String(bytes, StandardCharsets.UTF_8));
				}
			} else {
				try (BufferedReader reader = resource.openAsReader()) {
					long parseStartNs = ModelParseTimings.start();
					model = CuboidModel.fromStream(reader);
					ModelParseTimings.recordParse(parseStartNs);
				}
			}
			return Pair.of(modelId, model);
		} catch (Exception e) {
			PackForge.LOGGER.error("Failed to load model {}", entry.getKey(), e);
			return null;
		}
	}

	private static UnbakedModel parseString(String json) {
		long parseStartNs = ModelParseTimings.start();
		try (StringReader reader = new StringReader(json)) {
			UnbakedModel model = CuboidModel.fromStream(reader);
			ModelParseTimings.recordParse(parseStartNs);
			return model;
		} catch (Exception e) {
			throw new IllegalArgumentException("Failed duplicate model parse", e);
		}
	}

	private static int modelBatchSize(int modelCount) {
		int configured = Math.max(8, FeatureFlags.modelParseBatchSize());
		if (!FeatureFlags.modelAdaptiveBatchingEnabled()) {
			return configured;
		}
		if (modelCount >= 4096) return Math.max(configured, 128);
		if (modelCount >= 2048) return Math.max(configured, 96);
		if (modelCount <= 512) return Math.min(configured, 32);
		return configured;
	}

	private static final class ContentKey {
		private final byte[] bytes;
		private final int hash;

		private ContentKey(byte[] bytes) {
			this.bytes = bytes.clone();
			this.hash = Arrays.hashCode(this.bytes);
		}

		@Override
		public boolean equals(Object obj) {
			return this == obj || obj instanceof ContentKey other && Arrays.equals(this.bytes, other.bytes);
		}

		@Override
		public int hashCode() {
			return hash;
		}
	}

	private ModelParseOptimizer() {}
}
