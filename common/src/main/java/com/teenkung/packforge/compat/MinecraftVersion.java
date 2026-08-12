package com.teenkung.packforge.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Numeric Minecraft release comparison without depending on a loader's version library. */
public final class MinecraftVersion {
	public static boolean isAtLeast(String runtimeVersion, String minimumVersion) {
		Optional<List<Integer>> runtime = parse(runtimeVersion);
		Optional<List<Integer>> minimum = parse(minimumVersion);
		return runtime.isPresent() && minimum.isPresent() && compare(runtime.get(), minimum.get()) >= 0;
	}

	public static Optional<List<Integer>> parse(String value) {
		if (value == null) {
			return Optional.empty();
		}
		String normalized = value.trim();
		if (!normalized.matches("\\d+(?:\\.\\d+)*")) {
			return Optional.empty();
		}
		List<Integer> parts = new ArrayList<>();
		try {
			for (String token : normalized.split("\\.")) {
				parts.add(Integer.parseInt(token));
			}
		} catch (NumberFormatException ignored) {
			return Optional.empty();
		}
		return Optional.of(List.copyOf(parts));
	}

	private static int compare(List<Integer> left, List<Integer> right) {
		int size = Math.max(left.size(), right.size());
		for (int index = 0; index < size; index++) {
			int leftPart = index < left.size() ? left.get(index) : 0;
			int rightPart = index < right.size() ? right.get(index) : 0;
			int compared = Integer.compare(leftPart, rightPart);
			if (compared != 0) {
				return compared;
			}
		}
		return 0;
	}

	private MinecraftVersion() {}
}
