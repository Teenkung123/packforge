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
			return CompletableFuture.supplyAsync(() -> loadSerial(manager), executor);
		}
		return CompletableFuture.supplyAsync(() -> MODEL_LISTER.listMatchingResources(manager), executor).thenCompose(resources -> {
			List<Map.Entry<Identifier, Resource>> entries = List.copyOf(resources.entrySet());
			int batchSize = Math.max(8, FeatureFlags.modelParseBatchSize());
			ArrayList<CompletableFuture<List<Pair<Identifier, UnbakedModel>>>> jobs = new ArrayList<>();
			for (int from = 0; from < entries.size(); from += batchSize) {
				int start = from;
				int end = Math.min(entries.size(), from + batchSize);
				jobs.add(CompletableFuture.supplyAsync(() -> parseBatch(entries.subList(start, end)), executor));
			}
			return Util.sequence(jobs).thenApply(batches -> batches.stream()
				.flatMap(List::stream)
				.filter(Objects::nonNull)
				.collect(Collectors.toUnmodifiableMap(Pair::getFirst, Pair::getSecond)));
		});
	}

	public static void resetForReload() {
		DUPLICATE_CACHE.clear();
	}

	private static Map<Identifier, UnbakedModel> loadSerial(ResourceManager manager) {
		return MODEL_LISTER.listMatchingResources(manager).entrySet().stream()
			.map(ModelParseOptimizer::parseOne)
			.filter(Objects::nonNull)
			.collect(Collectors.toUnmodifiableMap(Pair::getFirst, Pair::getSecond));
	}

	private static List<Pair<Identifier, UnbakedModel>> parseBatch(List<Map.Entry<Identifier, Resource>> entries) {
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
			if (FeatureFlags.modelDuplicateParseCacheEnabled()) {
				byte[] bytes;
				try (var stream = resource.open()) {
					bytes = stream.readAllBytes();
				}
				ContentKey key = new ContentKey(bytes);
				model = DUPLICATE_CACHE.computeIfAbsent(key, ignored -> parseString(new String(bytes, StandardCharsets.UTF_8)));
			} else {
				try (BufferedReader reader = resource.openAsReader()) {
					model = CuboidModel.fromStream(reader);
				}
			}
			return Pair.of(modelId, model);
		} catch (Exception e) {
			PackForge.LOGGER.error("Failed to load model {}", entry.getKey(), e);
			return null;
		}
	}

	private static UnbakedModel parseString(String json) {
		try (StringReader reader = new StringReader(json)) {
			return CuboidModel.fromStream(reader);
		} catch (Exception e) {
			throw new IllegalArgumentException("Failed duplicate model parse", e);
		}
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
