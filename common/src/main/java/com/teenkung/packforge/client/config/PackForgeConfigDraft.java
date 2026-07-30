package com.teenkung.packforge.client.config;

import com.teenkung.packforge.config.PackForgeConfig;

import java.util.List;
import java.util.Objects;

/**
 * Detached screen-editing state. Unsupported options remain copied through
 * unchanged when a legacy screen applies its supported subset.
 */
public final class PackForgeConfigDraft {
	private PackForgeConfig.Cfg baseline;
	private PackForgeConfig.Cfg working;

	public PackForgeConfigDraft() {
		this(PackForgeConfig.get());
	}

	public PackForgeConfigDraft(PackForgeConfig.Cfg source) {
		this.baseline = PackForgeConfig.copyOf(Objects.requireNonNull(source, "source"));
		this.working = PackForgeConfig.copyOf(source);
	}

	public PackForgeConfig.Cfg working() {
		return working;
	}

	public boolean dirty() {
		return !PackForgeConfigScreenModel.sameValues(baseline, working);
	}

	public void reset(PackForgeConfigScreenModel.OptionSpec option) {
		Objects.requireNonNull(option, "option").reset(working);
	}

	public void resetAll(List<PackForgeConfigScreenModel.OptionSpec> visibleOptions) {
		for (PackForgeConfigScreenModel.OptionSpec option : visibleOptions) {
			option.reset(working);
		}
	}

	public void discard() {
		working = PackForgeConfig.copyOf(baseline);
	}

	public PackForgeConfig.SaveResult apply() {
		PackForgeConfig.SaveResult result = PackForgeConfig.applyAndSave(working);
		if (result.successful()) {
			baseline = PackForgeConfig.copyOf(PackForgeConfig.get());
			working = PackForgeConfig.copyOf(baseline);
		}
		return result;
	}
}
