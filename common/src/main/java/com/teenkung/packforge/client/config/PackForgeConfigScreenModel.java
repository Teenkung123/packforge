package com.teenkung.packforge.client.config;

import com.teenkung.packforge.config.OptimizationPlan;
import com.teenkung.packforge.config.PackForgeCapabilities;
import com.teenkung.packforge.config.PackForgeCapability;
import com.teenkung.packforge.config.PackForgeConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Minecraft-independent description of the PackForge configuration screen.
 *
 * <p>Version adapters render these entries with their native widget APIs. The
 * model is authoritative for capability visibility, validation bounds, reset
 * behavior, translations, and apply timing.</p>
 */
public final class PackForgeConfigScreenModel {
	public enum Category {
		RELOAD("reload"),
		ATLAS("atlas"),
		STARTUP("startup");

		private final String id;

		Category(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}

		public String translationKey() {
			return "packforge.config.category." + id;
		}
	}

	public enum ApplyScope {
		RESOURCE_RELOAD("resource_reload"),
		GAME_RESTART("game_restart");

		private final String id;

		ApplyScope(String id) {
			this.id = id;
		}

		public String translationKey() {
			return "packforge.config.apply." + id;
		}
	}

	public sealed interface OptionSpec permits BooleanOption, IntegerOption, StringListOption {
		String id();

		Category category();

		String section();

		PackForgeCapability capability();

		ApplyScope applyScope();

		default String titleKey() {
			return "packforge.config.option." + id();
		}

		default String descriptionKey() {
			return titleKey() + ".description";
		}

		default String sectionKey() {
			return "packforge.config.section." + section();
		}

		default boolean supported(Set<PackForgeCapability> capabilities) {
			return capabilities.contains(capability());
		}

		void reset(PackForgeConfig.Cfg target);

		boolean sameValue(PackForgeConfig.Cfg left, PackForgeConfig.Cfg right);
	}

	public record BooleanOption(
		String id,
		Category category,
		String section,
		PackForgeCapability capability,
		ApplyScope applyScope,
		Function<PackForgeConfig.Cfg, Boolean> getter,
		BiConsumer<PackForgeConfig.Cfg, Boolean> setter
	) implements OptionSpec {
		public BooleanOption {
			validateCommon(id, category, section, capability, applyScope);
			Objects.requireNonNull(getter, "getter");
			Objects.requireNonNull(setter, "setter");
		}

		public boolean get(PackForgeConfig.Cfg cfg) {
			return getter.apply(cfg);
		}

		public void set(PackForgeConfig.Cfg cfg, boolean value) {
			setter.accept(cfg, value);
		}

		@Override
		public void reset(PackForgeConfig.Cfg target) {
			set(target, get(DEFAULTS));
		}

		@Override
		public boolean sameValue(PackForgeConfig.Cfg left, PackForgeConfig.Cfg right) {
			return get(left) == get(right);
		}
	}

	public record IntegerOption(
		String id,
		Category category,
		String section,
		PackForgeCapability capability,
		ApplyScope applyScope,
		int minimum,
		int maximum,
		ToIntFunction<PackForgeConfig.Cfg> getter,
		IntSetter setter
	) implements OptionSpec {
		public IntegerOption {
			validateCommon(id, category, section, capability, applyScope);
			if (minimum > maximum) {
				throw new IllegalArgumentException("minimum must not exceed maximum");
			}
			Objects.requireNonNull(getter, "getter");
			Objects.requireNonNull(setter, "setter");
		}

		public int get(PackForgeConfig.Cfg cfg) {
			return getter.applyAsInt(cfg);
		}

		public void set(PackForgeConfig.Cfg cfg, int value) {
			if (!valid(value)) {
				throw new IllegalArgumentException(id + " must be between " + minimum + " and " + maximum);
			}
			setter.accept(cfg, value);
		}

		public boolean valid(int value) {
			return value >= minimum && value <= maximum;
		}

		@Override
		public void reset(PackForgeConfig.Cfg target) {
			set(target, get(DEFAULTS));
		}

		@Override
		public boolean sameValue(PackForgeConfig.Cfg left, PackForgeConfig.Cfg right) {
			return get(left) == get(right);
		}
	}

	public record StringListOption(
		String id,
		Category category,
		String section,
		PackForgeCapability capability,
		ApplyScope applyScope,
		Function<PackForgeConfig.Cfg, List<String>> getter,
		BiConsumer<PackForgeConfig.Cfg, List<String>> setter
	) implements OptionSpec {
		public StringListOption {
			validateCommon(id, category, section, capability, applyScope);
			Objects.requireNonNull(getter, "getter");
			Objects.requireNonNull(setter, "setter");
		}

		public List<String> get(PackForgeConfig.Cfg cfg) {
			return List.copyOf(getter.apply(cfg));
		}

		public String format(PackForgeConfig.Cfg cfg) {
			return String.join(", ", get(cfg));
		}

		public void set(PackForgeConfig.Cfg cfg, String value) {
			setter.accept(cfg, parseList(value));
		}

		@Override
		public void reset(PackForgeConfig.Cfg target) {
			setter.accept(target, new ArrayList<>(get(DEFAULTS)));
		}

		@Override
		public boolean sameValue(PackForgeConfig.Cfg left, PackForgeConfig.Cfg right) {
			return get(left).equals(get(right));
		}
	}

	@FunctionalInterface
	public interface IntSetter {
		void accept(PackForgeConfig.Cfg cfg, int value);
	}

	private static final PackForgeConfig.Cfg DEFAULTS = new PackForgeConfig.Cfg();
	private static final List<OptionSpec> OPTIONS = buildOptions();

	public static List<OptionSpec> allOptions() {
		return OPTIONS;
	}

	public static List<OptionSpec> availableOptions() {
		return availableOptions(PackForgeCapabilities.available(), PackForgeCapabilities.target());
	}

	public static List<OptionSpec> availableOptions(Set<PackForgeCapability> capabilities) {
		return availableOptions(capabilities, PackForgeCapabilities.target());
	}

	public static List<OptionSpec> availableOptions(Set<PackForgeCapability> capabilities, String target) {
		Objects.requireNonNull(capabilities, "capabilities");
		Objects.requireNonNull(target, "target");
		boolean mc26Preparation = target.equals("mc26") || target.startsWith("mc26_");
		return OPTIONS.stream()
			.filter(option -> option.supported(capabilities))
			// These batch sizes have no effect on the mc26 preparation adapters.
			.filter(option -> !mc26Preparation || switch (option.id()) {
				case "atlas_mip_batch_size", "atlas_decode_batch_size", "model_parse_batch_size" -> false;
				default -> true;
			})
			.toList();
	}

	public static List<Category> availableCategories(Set<PackForgeCapability> capabilities) {
		EnumSet<Category> categories = EnumSet.noneOf(Category.class);
		for (OptionSpec option : availableOptions(capabilities)) {
			categories.add(option.category());
		}
		return List.copyOf(categories);
	}

	public static boolean sameValues(PackForgeConfig.Cfg left, PackForgeConfig.Cfg right) {
		for (OptionSpec option : OPTIONS) {
			if (!option.sameValue(left, right)) {
				return false;
			}
		}
		return true;
	}

	/** Immutable preview. Nothing is written or enabled until its changes are applied. */
	public record RecommendedChange(BooleanOption option, boolean before, boolean after) {
		public void apply(PackForgeConfig.Cfg target) {
			option.set(target, after);
		}
	}

	public static List<RecommendedChange> recommendedChanges(PackForgeConfig.Cfg source) {
		return recommendedChanges(source, availableOptions());
	}

	public static PackForgeConfig.Cfg previewRecommendations(PackForgeConfig.Cfg source, List<RecommendedChange> changes) {
		PackForgeConfig.Cfg preview = PackForgeConfig.copyOf(source);
		for (RecommendedChange change : changes) change.apply(preview);
		return preview;
	}

	public static List<RecommendedChange> recommendedChanges(PackForgeConfig.Cfg source, List<OptionSpec> available) {
		Objects.requireNonNull(source, "source");
		List<RecommendedChange> changes = new ArrayList<>();
		for (OptionSpec spec : available) {
			if (!(spec instanceof BooleanOption option)) continue;
			Boolean recommended = switch (option.id()) {
				case "reload_optimizer", "loader_index", "font_provider_selection", "cpu_mip_preparation" -> true;
				case "loader_zip_pool", "resource_read_reuse", "font_bitmap_cache", "atlas_decode_batching",
					"model_adaptive_batching", "model_duplicate_cache", "loader_timings", "reload_listener_timings",
					"shader_stall_diagnostics", "font_reload_diagnostics", "atlas_phase_timings", "model_parse_timings",
					"startup_timings", "startup_executor_tuning", "startup_async_data", "startup_async_class_scan",
					"startup_async_font_atlas" -> false;
				default -> null;
			};
			if (recommended != null && option.get(source) != recommended) {
				changes.add(new RecommendedChange(option, option.get(source), recommended));
			}
		}
		return List.copyOf(changes);
	}

	/** Policy status for controls whose capability describes that setting itself. */
	public static OptimizationPlan.Decision optimizationDecision(PackForgeConfig.Cfg source, OptionSpec option) {
		return hasIndependentDecision(option) ? OptimizationPlan.capture(source).decision(option.capability()) : null;
	}

	static OptimizationPlan.Decision optimizationDecision(OptimizationPlan plan, OptionSpec option) {
		return hasIndependentDecision(option) ? plan.decision(option.capability()) : null;
	}

	private static boolean hasIndependentDecision(OptionSpec option) {
		if (!(option instanceof BooleanOption)) return false;
		return switch (option.id()) {
			// These borrow another feature's capability solely for availability.
			case "reload_optimizer", "large_atlas_fixer", "atlas_retry_disable_with_iris", "startup_skip_smooth_boot" -> false;
			default -> true;
		};
	}

	private static List<OptionSpec> buildOptions() {
		List<OptionSpec> options = new ArrayList<>();

		options.add(bool("reload_optimizer", Category.RELOAD, "general", PackForgeCapability.RESOURCE_PACK_INDEX, ApplyScope.RESOURCE_RELOAD,
			cfg -> cfg.reloadOptimizerEnabled, (cfg, value) -> cfg.reloadOptimizerEnabled = value));
		options.add(bool("loader_index", Category.RELOAD, "pack_index", PackForgeCapability.RESOURCE_PACK_INDEX, ApplyScope.RESOURCE_RELOAD,
			cfg -> cfg.loaderIndexEnabled, (cfg, value) -> cfg.loaderIndexEnabled = value));
		options.add(bool("loader_zip_pool", Category.RELOAD, "pack_index", PackForgeCapability.ZIP_READ_POOL, ApplyScope.RESOURCE_RELOAD,
			cfg -> cfg.loaderZipPoolEnabled, (cfg, value) -> cfg.loaderZipPoolEnabled = value));
		options.add(bool("resource_read_reuse", Category.RELOAD, "pack_index", PackForgeCapability.RESOURCE_READ_REUSE, ApplyScope.RESOURCE_RELOAD,
			cfg -> cfg.resourceReadReuseEnabled, (cfg, value) -> cfg.resourceReadReuseEnabled = value));
		options.add(integer("optimization_memory_mib", Category.RELOAD, "advanced", PackForgeCapability.RESOURCE_READ_REUSE, ApplyScope.RESOURCE_RELOAD,
			1, 128, cfg -> cfg.optimizationMemoryMiB, (cfg, value) -> cfg.optimizationMemoryMiB = value));
		options.add(bool("loading_status_overlay", Category.RELOAD, "reload_ui", PackForgeCapability.LOADING_STATUS_OVERLAY, ApplyScope.RESOURCE_RELOAD,
			cfg -> cfg.loadingStatusOverlayEnabled, (cfg, value) -> cfg.loadingStatusOverlayEnabled = value));
		options.add(bool("loading_fade_out_disabled", Category.RELOAD, "reload_ui", PackForgeCapability.LOADING_FADE_CONTROL, ApplyScope.RESOURCE_RELOAD,
			cfg -> cfg.loadingScreenFadeOutDisabled, (cfg, value) -> cfg.loadingScreenFadeOutDisabled = value));
		options.add(bool("reload_summary_toast", Category.RELOAD, "reload_ui", PackForgeCapability.RELOAD_SUMMARY_TOAST, ApplyScope.RESOURCE_RELOAD,
			cfg -> cfg.reloadSummaryToastEnabled, (cfg, value) -> cfg.reloadSummaryToastEnabled = value));
		options.add(bool("font_provider_selection", Category.RELOAD, "fonts", PackForgeCapability.FONT_PROVIDER_PRESELECTION, ApplyScope.RESOURCE_RELOAD,
			cfg -> cfg.fontPrepareProviderSelectionEnabled, (cfg, value) -> cfg.fontPrepareProviderSelectionEnabled = value));
		options.add(bool("font_bitmap_cache", Category.RELOAD, "fonts", PackForgeCapability.FONT_BITMAP_CACHE, ApplyScope.RESOURCE_RELOAD,
			cfg -> cfg.fontBitmapProviderCacheEnabled, (cfg, value) -> cfg.fontBitmapProviderCacheEnabled = value));
		options.add(bool("model_parse_batching", Category.RELOAD, "models", PackForgeCapability.MODEL_PARSE_BATCHING, ApplyScope.RESOURCE_RELOAD,
			cfg -> cfg.modelParseBatchingEnabled, (cfg, value) -> cfg.modelParseBatchingEnabled = value));
		options.add(integer("model_parse_batch_size", Category.RELOAD, "models", PackForgeCapability.MODEL_PARSE_BATCHING, ApplyScope.RESOURCE_RELOAD,
			8, 1024, cfg -> cfg.modelParseBatchSize, (cfg, value) -> cfg.modelParseBatchSize = value));
		options.add(bool("model_parse_timings", Category.RELOAD, "models", PackForgeCapability.MODEL_PARSE_TIMINGS, ApplyScope.RESOURCE_RELOAD,
			cfg -> cfg.modelParseTimingEnabled, (cfg, value) -> cfg.modelParseTimingEnabled = value));
		options.add(bool("model_adaptive_batching", Category.RELOAD, "models", PackForgeCapability.MODEL_ADAPTIVE_BATCHING, ApplyScope.RESOURCE_RELOAD,
			cfg -> cfg.modelAdaptiveBatchingEnabled, (cfg, value) -> cfg.modelAdaptiveBatchingEnabled = value));
		options.add(bool("model_duplicate_cache", Category.RELOAD, "models", PackForgeCapability.MODEL_DUPLICATE_CACHE, ApplyScope.RESOURCE_RELOAD,
			cfg -> cfg.modelDuplicateParseCacheEnabled, (cfg, value) -> cfg.modelDuplicateParseCacheEnabled = value));
		options.add(bool("loader_timings", Category.RELOAD, "logging", PackForgeCapability.LOADER_TIMINGS, ApplyScope.RESOURCE_RELOAD,
			cfg -> cfg.loaderTimingsEnabled, (cfg, value) -> cfg.loaderTimingsEnabled = value));
		options.add(bool("reload_listener_timings", Category.RELOAD, "logging", PackForgeCapability.RELOAD_LISTENER_TIMINGS, ApplyScope.RESOURCE_RELOAD,
			cfg -> cfg.reloadListenerTimingsEnabled, (cfg, value) -> cfg.reloadListenerTimingsEnabled = value));
		options.add(bool("shader_stall_diagnostics", Category.RELOAD, "logging", PackForgeCapability.SHADER_STALL_DIAGNOSTICS, ApplyScope.RESOURCE_RELOAD,
			cfg -> cfg.shaderApplyStallDiagnosticsEnabled, (cfg, value) -> cfg.shaderApplyStallDiagnosticsEnabled = value));
		options.add(bool("immediatelyfast_font_guard", Category.RELOAD, "compatibility", PackForgeCapability.IMMEDIATELY_FAST_FONT_ATLAS_COMPAT, ApplyScope.RESOURCE_RELOAD,
			cfg -> cfg.immediatelyFastFontAtlasCompatEnabled, (cfg, value) -> cfg.immediatelyFastFontAtlasCompatEnabled = value));
		options.add(bool("font_reload_diagnostics", Category.RELOAD, "logging", PackForgeCapability.FONT_RELOAD_DIAGNOSTICS, ApplyScope.RESOURCE_RELOAD,
			cfg -> cfg.fontReloadDiagnosticsEnabled, (cfg, value) -> cfg.fontReloadDiagnosticsEnabled = value));
		options.add(bool("atlas_phase_timings", Category.RELOAD, "logging", PackForgeCapability.ATLAS_PHASE_TIMINGS, ApplyScope.RESOURCE_RELOAD,
			cfg -> cfg.atlasPhaseTimingsEnabled, (cfg, value) -> cfg.atlasPhaseTimingsEnabled = value));
		options.add(bool("atlas_decode_batching", Category.RELOAD, "atlas_decode", PackForgeCapability.ATLAS_DECODE_BATCHING, ApplyScope.RESOURCE_RELOAD,
			cfg -> cfg.atlasDecodeBatchingEnabled, (cfg, value) -> cfg.atlasDecodeBatchingEnabled = value));
		options.add(integer("atlas_decode_batch_size", Category.RELOAD, "atlas_decode", PackForgeCapability.ATLAS_DECODE_BATCHING, ApplyScope.RESOURCE_RELOAD,
			16, 4096, cfg -> cfg.atlasDecodeBatchSize, (cfg, value) -> cfg.atlasDecodeBatchSize = value));

		options.add(bool("large_atlas_fixer", Category.ATLAS, "general", PackForgeCapability.ATLAS_CAP, ApplyScope.RESOURCE_RELOAD,
			cfg -> cfg.largeAtlasFixerEnabled, (cfg, value) -> cfg.largeAtlasFixerEnabled = value));
		options.add(bool("model_uv_clamp", Category.ATLAS, "compatibility", PackForgeCapability.MODEL_UV_TRANSPARENCY_CLAMP, ApplyScope.RESOURCE_RELOAD,
			cfg -> cfg.modelUvTransparencyClampEnabled, (cfg, value) -> cfg.modelUvTransparencyClampEnabled = value));
		options.add(bool("atlas_cap", Category.ATLAS, "atlas_cap", PackForgeCapability.ATLAS_CAP, ApplyScope.RESOURCE_RELOAD,
			cfg -> cfg.atlasCapEnabled, (cfg, value) -> cfg.atlasCapEnabled = value));
		options.add(integer("atlas_cap_pixels", Category.ATLAS, "atlas_cap", PackForgeCapability.ATLAS_CAP, ApplyScope.RESOURCE_RELOAD,
			16, 8192, cfg -> cfg.atlasCapPx, (cfg, value) -> cfg.atlasCapPx = value));
		options.add(stringList("atlas_exclude_ids", Category.ATLAS, "atlas_cap", PackForgeCapability.ATLAS_CAP, ApplyScope.RESOURCE_RELOAD,
			cfg -> cfg.atlasExcludeIds, (cfg, value) -> cfg.atlasExcludeIds = new ArrayList<>(value)));
		options.add(bool("atlas_retry", Category.ATLAS, "atlas_retry", PackForgeCapability.ATLAS_RETRY, ApplyScope.RESOURCE_RELOAD,
			cfg -> cfg.atlasRetryEnabled, (cfg, value) -> cfg.atlasRetryEnabled = value));
		options.add(integer("atlas_retry_attempts", Category.ATLAS, "atlas_retry", PackForgeCapability.ATLAS_RETRY, ApplyScope.RESOURCE_RELOAD,
			1, 10, cfg -> cfg.atlasRetryMaxAttempts, (cfg, value) -> cfg.atlasRetryMaxAttempts = value));
		options.add(bool("atlas_retry_disable_with_iris", Category.ATLAS, "atlas_retry", PackForgeCapability.ATLAS_RETRY, ApplyScope.GAME_RESTART,
			cfg -> cfg.forceDisablePartIIIWithIris, (cfg, value) -> cfg.forceDisablePartIIIWithIris = value));
		options.add(bool("cpu_mip_preparation", Category.ATLAS, "atlas_mipmaps", PackForgeCapability.ATLAS_MIP_PARALLEL, ApplyScope.RESOURCE_RELOAD,
			cfg -> cfg.cpuMipPreparationEnabled, (cfg, value) -> cfg.cpuMipPreparationEnabled = value));
		options.add(integer("atlas_mip_batch_size", Category.ATLAS, "atlas_mipmaps", PackForgeCapability.ATLAS_MIP_PARALLEL, ApplyScope.RESOURCE_RELOAD,
			16, 4096, cfg -> cfg.atlasMipBatchSize, (cfg, value) -> cfg.atlasMipBatchSize = value));

		options.add(bool("startup_optimizer", Category.STARTUP, "general", PackForgeCapability.STARTUP_OPTIMIZER, ApplyScope.GAME_RESTART,
			cfg -> cfg.startupOptimizerEnabled, (cfg, value) -> cfg.startupOptimizerEnabled = value));
		options.add(bool("startup_timings", Category.STARTUP, "general", PackForgeCapability.STARTUP_TIMINGS, ApplyScope.GAME_RESTART,
			cfg -> cfg.startupTimingsEnabled, (cfg, value) -> cfg.startupTimingsEnabled = value));
		options.add(bool("startup_status_overlay", Category.STARTUP, "general", PackForgeCapability.STARTUP_STATUS_OVERLAY, ApplyScope.GAME_RESTART,
			cfg -> cfg.startupStatusOverlayEnabled, (cfg, value) -> cfg.startupStatusOverlayEnabled = value));
		options.add(bool("startup_executor_tuning", Category.STARTUP, "executors", PackForgeCapability.STARTUP_EXECUTOR_TUNING, ApplyScope.GAME_RESTART,
			cfg -> cfg.startupExecutorTuningEnabled, (cfg, value) -> cfg.startupExecutorTuningEnabled = value));
		options.add(integer("startup_worker_threads", Category.STARTUP, "executors", PackForgeCapability.STARTUP_EXECUTOR_TUNING, ApplyScope.GAME_RESTART,
			0, Runtime.getRuntime().availableProcessors(), cfg -> cfg.startupWorkerThreads, (cfg, value) -> cfg.startupWorkerThreads = value));
		options.add(integer("startup_thread_priority", Category.STARTUP, "executors", PackForgeCapability.STARTUP_EXECUTOR_TUNING, ApplyScope.GAME_RESTART,
			Thread.MIN_PRIORITY, Thread.MAX_PRIORITY, cfg -> cfg.startupThreadPriority, (cfg, value) -> cfg.startupThreadPriority = value));
		options.add(bool("startup_skip_smooth_boot", Category.STARTUP, "compatibility", PackForgeCapability.STARTUP_EXECUTOR_TUNING, ApplyScope.GAME_RESTART,
			cfg -> cfg.startupSkipWithSmoothBoot, (cfg, value) -> cfg.startupSkipWithSmoothBoot = value));
		options.add(bool("startup_async_data", Category.STARTUP, "future_async", PackForgeCapability.STARTUP_ASYNC_DATA, ApplyScope.GAME_RESTART,
			cfg -> cfg.startupAsyncDataParsingEnabled, (cfg, value) -> cfg.startupAsyncDataParsingEnabled = value));
		options.add(bool("startup_async_class_scan", Category.STARTUP, "future_async", PackForgeCapability.STARTUP_ASYNC_CLASS_SCAN, ApplyScope.GAME_RESTART,
			cfg -> cfg.startupAsyncClassScanEnabled, (cfg, value) -> cfg.startupAsyncClassScanEnabled = value));
		options.add(bool("startup_async_font_atlas", Category.STARTUP, "future_async", PackForgeCapability.STARTUP_ASYNC_FONT_ATLAS, ApplyScope.GAME_RESTART,
			cfg -> cfg.startupAsyncFontAtlasEnabled, (cfg, value) -> cfg.startupAsyncFontAtlasEnabled = value));

		return Collections.unmodifiableList(options);
	}

	private static BooleanOption bool(
		String id,
		Category category,
		String section,
		PackForgeCapability capability,
		ApplyScope applyScope,
		Function<PackForgeConfig.Cfg, Boolean> getter,
		BiConsumer<PackForgeConfig.Cfg, Boolean> setter
	) {
		return new BooleanOption(id, category, section, capability, applyScope, getter, setter);
	}

	private static IntegerOption integer(
		String id,
		Category category,
		String section,
		PackForgeCapability capability,
		ApplyScope applyScope,
		int minimum,
		int maximum,
		ToIntFunction<PackForgeConfig.Cfg> getter,
		IntSetter setter
	) {
		return new IntegerOption(id, category, section, capability, applyScope, minimum, maximum, getter, setter);
	}

	private static StringListOption stringList(
		String id,
		Category category,
		String section,
		PackForgeCapability capability,
		ApplyScope applyScope,
		Function<PackForgeConfig.Cfg, List<String>> getter,
		BiConsumer<PackForgeConfig.Cfg, List<String>> setter
	) {
		return new StringListOption(id, category, section, capability, applyScope, getter, setter);
	}

	private static void validateCommon(
		String id,
		Category category,
		String section,
		PackForgeCapability capability,
		ApplyScope applyScope
	) {
		if (id == null || id.isBlank()) {
			throw new IllegalArgumentException("option id must not be blank");
		}
		if (section == null || section.isBlank()) {
			throw new IllegalArgumentException("section must not be blank");
		}
		Objects.requireNonNull(category, "category");
		Objects.requireNonNull(capability, "capability");
		Objects.requireNonNull(applyScope, "applyScope");
	}

	private static List<String> parseList(String value) {
		if (value == null || value.isBlank()) {
			return List.of();
		}
		List<String> result = new ArrayList<>();
		for (String part : value.split(",")) {
			String trimmed = part.trim();
			if (!trimmed.isEmpty() && !result.contains(trimmed)) {
				result.add(trimmed);
			}
		}
		return result;
	}

	private PackForgeConfigScreenModel() {}
}
