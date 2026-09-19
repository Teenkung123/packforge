package com.teenkung.packforge.client.font;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.internal.LazilyParsedNumber;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.UnboundedMapCodec;
import com.teenkung.packforge.concurrent.PreparationBudget;
import com.teenkung.packforge.loader.ReloadExecutionContext;
import net.minecraft.util.ExtraCodecs;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/** Linear successful decoding for the vanilla SpaceProvider advances field only. */
public final class SpaceAdvanceCodec extends MapCodec<Map<Integer, Float>> {
	private static final int MIN_ENTRIES = 256;
	private static final Class<?> VANILLA_FIELD_TYPE = Codec.unboundedMap(ExtraCodecs.CODEPOINT, Codec.FLOAT).fieldOf("advances").getClass();
	private static final String VANILLA_FIELD_DESCRIPTION = Codec.unboundedMap(ExtraCodecs.CODEPOINT, Codec.FLOAT).fieldOf("advances").toString();
	private static final Class<?> JSON_MAP_TYPE = JsonOps.INSTANCE.getMap(new JsonObject()).result().orElseThrow().getClass();
	private final MapCodec<Map<Integer, Float>> original;
	private final Runnable onAdmission;

	private SpaceAdvanceCodec(MapCodec<Map<Integer, Float>> original, Runnable onAdmission) {
		this.original = original; this.onAdmission = onAdmission;
	}

	public static MapCodec<Map<Integer, Float>> wrap(UnboundedMapCodec<Integer, Float> source, String field,
		MapCodec<Map<Integer, Float>> original) {
		return wrap(source, field, original, null);
	}

	/** Package-private admission observation for deterministic lifecycle verification. */
	static MapCodec<Map<Integer, Float>> wrap(UnboundedMapCodec<Integer, Float> source, String field,
		MapCodec<Map<Integer, Float>> original, Runnable onAdmission) {
		if (!"advances".equals(field) || source.keyCodec() != ExtraCodecs.CODEPOINT || source.elementCodec() != Codec.FLOAT
			|| source.getClass() != UnboundedMapCodec.class || original.getClass() != VANILLA_FIELD_TYPE
			|| !VANILLA_FIELD_DESCRIPTION.equals(original.toString())) return original;
		return new SpaceAdvanceCodec(original, onAdmission);
	}

	@Override public <T> DataResult<Map<Integer, Float>> decode(DynamicOps<T> ops, MapLike<T> input) {
		ReloadExecutionContext context = ReloadExecutionContext.current();
		FontPreparationCoordinator coordinator = FontPreparationCoordinator.current();
		if (context == null || coordinator == null || !context.features().fontPrepareProviderSelectionEnabled()) return original.decode(ops, input);
		Outcome admission = coordinator.admitSpaceCodec();
		if (admission != Outcome.ADMITTED) return fallback(ops, input, coordinator, 0, admission);
		if (ops != JsonOps.INSTANCE || input.getClass() != JSON_MAP_TYPE) return fallback(ops, input, coordinator, 0, Outcome.INPUT);
		Object raw = input.get("advances");
		int count = raw instanceof JsonObject object ? object.size() : 0;
		if (!(raw instanceof JsonObject object)) return fallback(ops, input, coordinator, count, Outcome.INPUT);
		if (count < MIN_ENTRIES) return fallback(ops, input, coordinator, count, Outcome.SMALL);
		PreparationBudget.Scope scope = context.preparationBudget();
		// The immutable output replaces vanilla's required output. Account additional
		// hash-builder nodes/table, parse temporaries and bookkeeping until that builder
		// leaves scope; retain no extra map or reference after successful decoding.
		PreparationBudget.Reservation scratch = scope.tryReserve(8192L + count * 128L);
		if (scratch == null) return fallback(ops, input, coordinator, count, Outcome.BUDGET);
		DataResult<Map<Integer, Float>> decoded;
		boolean retired;
		try {
			if (onAdmission != null) onAdmission.run();
			decoded = !scope.isRetired() && eligible(object) ? decodeJson(object) : null;
			retired = scope.isRetired();
			if (retired) decoded = null;
		}
		finally { scratch.close(); }
		if (decoded == null) return fallback(ops, input, coordinator, count, retired ? Outcome.STALE : Outcome.INPUT);
		coordinator.recordSpaceCodec(count, Outcome.ADMITTED);
		return decoded;
	}

	private <T> DataResult<Map<Integer, Float>> fallback(DynamicOps<T> ops, MapLike<T> input,
		FontPreparationCoordinator coordinator, int count, Outcome outcome) {
		coordinator.recordSpaceCodec(count, outcome);
		return original.decode(ops, input);
	}

	private static boolean eligible(JsonObject input) {
		for (Map.Entry<String, JsonElement> entry : input.entrySet()) {
			String key = entry.getKey();
			// Avoid speculative CODEPOINT allocations for malformed, arbitrarily long keys.
			if (key.length() > 2 || key.codePointCount(0, key.length()) != 1) return false;
			if (!(entry.getValue() instanceof JsonPrimitive value) || !value.isNumber()) return false;
			Class<?> number = value.getAsNumber().getClass();
			if (number != LazilyParsedNumber.class && number != BigDecimal.class && number != BigInteger.class
				&& number != Byte.class && number != Short.class && number != Integer.class && number != Long.class
				&& number != Float.class && number != Double.class) return false;
		}
		return true;
	}

	/** The hash builder is unreachable before its caller releases scratch admission. */
	private static DataResult<Map<Integer, Float>> decodeJson(JsonObject input) {
		Map<Integer, Float> decoded = LinkedHashMap.newLinkedHashMap(input.size());
		Lifecycle lifecycle = Lifecycle.stable();
		for (Map.Entry<String, JsonElement> entry : input.entrySet()) {
			DataResult<Integer> key = ExtraCodecs.CODEPOINT.parse(JsonOps.INSTANCE, new JsonPrimitive(entry.getKey()));
			DataResult<Float> value;
			try { value = Codec.FLOAT.parse(JsonOps.INSTANCE, entry.getValue()); }
			catch (NumberFormatException malformedNumber) { return null; }
			if (key.isError() || value.isError()) return null;
			Integer codepoint = key.result().orElse(null);
			Float advance = value.result().orElse(null);
			if (codepoint == null || advance == null || decoded.putIfAbsent(codepoint, advance) != null) return null;
			lifecycle = lifecycle.add(key.lifecycle()).add(value.lifecycle());
		}
		return DataResult.success(ImmutableMap.copyOf(decoded), lifecycle);
	}

	@Override public <T> RecordBuilder<T> encode(Map<Integer, Float> input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
		return original.encode(input, ops, prefix);
	}

	@Override public <T> Stream<T> keys(DynamicOps<T> ops) { return original.keys(ops); }
	@Override public String toString() { return original.toString(); }

	enum Outcome { ADMITTED, SMALL, INPUT, BUDGET, STALE }
}
