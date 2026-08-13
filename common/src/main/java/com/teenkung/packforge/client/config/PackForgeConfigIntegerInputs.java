package com.teenkung.packforge.client.config;

import com.teenkung.packforge.config.PackForgeConfig;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Tracks transient integer-editor validity without mutating a draft for invalid text. */
final class PackForgeConfigIntegerInputs {
	private final Set<String> invalidOptionIds = new HashSet<>();

	boolean update(PackForgeConfigScreenModel.IntegerOption option, PackForgeConfig.Cfg config, String rawValue) {
		Objects.requireNonNull(option, "option");
		Objects.requireNonNull(config, "config");
		Objects.requireNonNull(rawValue, "rawValue");
		try {
			int value = Integer.parseInt(rawValue.trim());
			if (!option.valid(value)) {
				invalidOptionIds.add(option.id());
				return false;
			}
			option.set(config, value);
			invalidOptionIds.remove(option.id());
			return true;
		} catch (NumberFormatException ignored) {
			invalidOptionIds.add(option.id());
			return false;
		}
	}

	void reset(PackForgeConfigScreenModel.OptionSpec option) {
		invalidOptionIds.remove(option.id());
	}

	void clear() {
		invalidOptionIds.clear();
	}

	boolean hasInvalid() {
		return !invalidOptionIds.isEmpty();
	}

	boolean isInvalid(PackForgeConfigScreenModel.OptionSpec option) {
		return invalidOptionIds.contains(option.id());
	}
}
