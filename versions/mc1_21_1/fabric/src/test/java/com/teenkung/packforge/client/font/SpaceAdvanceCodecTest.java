package com.teenkung.packforge.client.font;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.internal.LazilyParsedNumber;
import com.mojang.blaze3d.font.SpaceProvider;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.codecs.UnboundedMapCodec;
import com.teenkung.packforge.concurrent.PreparationBudget;
import com.teenkung.packforge.config.ReloadFeatureSnapshot;
import com.teenkung.packforge.loader.ReloadExecutionContext;
import net.minecraft.util.ExtraCodecs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class SpaceAdvanceCodecTest {
	private static final UnboundedMapCodec<Integer, Float> SOURCE = Codec.unboundedMap(ExtraCodecs.CODEPOINT, Codec.FLOAT);
	private static final MapCodec<Map<Integer, Float>> ORIGINAL = SOURCE.fieldOf("advances");
	private static final MapCodec<Map<Integer, Float>> CANDIDATE = SpaceAdvanceCodec.wrap(SOURCE, "advances", ORIGINAL);
	private ReloadExecutionContext context;
	private FontPreparationCoordinator coordinator;
	private long bookkeeping;

	@BeforeEach void start() {
		ReloadFeatureSnapshot features = ReloadFeatureSnapshot.capture();
		assertTrue(features.fontPrepareProviderSelectionEnabled());
		assertNotSame(ORIGINAL, CANDIDATE);
		context = ReloadExecutionContext.startForTesting(features);
		coordinator = new FontPreparationCoordinator(context.preparationBudget(), Set.of(), true);
		bookkeeping = context.preparationBudget().used();
	}

	@AfterEach void finish() {
		coordinator.close();
		ReloadExecutionContext.finish(context);
		assertEquals(0, context.preparationBudget().used());
	}

	@Test void successfulLargeMapMatchesVanillaLifecycleOrderEncodingAndFloatBits() {
		JsonObject input = input(1024);
		JsonObject advances = input.getAsJsonObject("advances");
		advances.addProperty(" ", -0.0f);
		advances.addProperty("A", -2.25f);
		advances.addProperty("B", Float.NaN);
		advances.addProperty("C", Float.POSITIVE_INFINITY);
		advances.addProperty("D", Float.NEGATIVE_INFINITY);
		advances.addProperty(Character.toString(0x1f600), 1000.5f);
		advances.addProperty("\ud800", 9f);
		DataResult<Map<Integer, Float>> expected = ORIGINAL.codec().parse(JsonOps.INSTANCE, input);
		DataResult<Map<Integer, Float>> actual = parse(input, JsonOps.INSTANCE);
		assertEquivalent(expected, actual);
		assertEquivalent(SpaceProvider.Definition.CODEC.codec().parse(JsonOps.INSTANCE, input).map(SpaceProvider.Definition::advances), actual);
		Map<Integer, Float> values = actual.result().orElseThrow();
		assertEquals(ORIGINAL.codec().encodeStart(JsonOps.INSTANCE, values).resultOrPartial(), CANDIDATE.codec().encodeStart(JsonOps.INSTANCE, values).resultOrPartial());
		assertEquals(ORIGINAL.keys(JsonOps.INSTANCE).toList(), CANDIDATE.keys(JsonOps.INSTANCE).toList());
		assertThrows(UnsupportedOperationException.class, () -> values.put(65, 99f));
		assertEquals(1, coordinator.diagnostics().spaceCodecAdmitted());
		assertEquals(advances.size(), coordinator.diagnostics().spaceCodecAdmittedEntries());
		assertEquals(bookkeeping, context.preparationBudget().used(), "Result is vanilla-owned; no optimization map remains retained");
	}

	@Test void thresholdKeepsSmallMapsOriginalAndAdmitsAt256Entries() {
		assertEquivalent(ORIGINAL.codec().parse(JsonOps.INSTANCE, input(255)), parse(input(255), JsonOps.INSTANCE));
		assertEquivalent(ORIGINAL.codec().parse(JsonOps.INSTANCE, input(256)), parse(input(256), JsonOps.INSTANCE));
		assertEquals(1, coordinator.diagnostics().spaceCodecSmallBypasses());
		assertEquals(1, coordinator.diagnostics().spaceCodecAdmitted());
		assertEquals(511, coordinator.diagnostics().spaceCodecEntries());
	}

	@Test void malformedEntriesPreserveOriginalErrorsPartialValuesAndDiagnostics() {
		for (JsonElement invalid : List.of(JsonNull.INSTANCE, new JsonPrimitive("3"), new JsonPrimitive(true), new JsonArray(), new JsonObject())) {
			JsonObject input = input(256);
			input.getAsJsonObject("advances").add("A", invalid);
			assertEquivalent(ORIGINAL.codec().parse(JsonOps.INSTANCE, input), parse(input, JsonOps.INSTANCE));
		}
		for (String invalidKey : List.of("", "AB", "three", "\ud800x")) {
			JsonObject input = input(256);
			input.getAsJsonObject("advances").addProperty(invalidKey, 3f);
			assertEquivalent(ORIGINAL.codec().parse(JsonOps.INSTANCE, input), parse(input, JsonOps.INSTANCE));
		}
		assertEquals(0, coordinator.diagnostics().spaceCodecAdmitted());
		assertEquals(bookkeeping, context.preparationBudget().used());
	}

	@Test void missingWrongTypedAndCompressedInputsKeepOriginalPath() {
		for (JsonElement input : List.of(new JsonObject(), JsonNull.INSTANCE, new JsonArray(), new JsonPrimitive(3))) {
			assertEquivalent(ORIGINAL.codec().parse(JsonOps.INSTANCE, input), parse(input, JsonOps.INSTANCE));
		}
		JsonObject input = input(256);
		input.getAsJsonObject("advances").addProperty("A", "3");
		assertEquivalent(ORIGINAL.codec().parse(JsonOps.COMPRESSED, input), parse(input, JsonOps.COMPRESSED));
		assertEquals(0, coordinator.diagnostics().spaceCodecAdmitted());
	}

	@Test void gsonDuplicateNameWinnerAndSupplementaryCharactersRemainUnchanged() {
		JsonObject input = input(256);
		JsonObject duplicate = JsonParser.parseString("{\"A\":1,\"A\":2}").getAsJsonObject();
		duplicate.entrySet().forEach(entry -> input.getAsJsonObject("advances").add(entry.getKey(), entry.getValue()));
		assertEquivalent(ORIGINAL.codec().parse(JsonOps.INSTANCE, input), parse(input, JsonOps.INSTANCE));
		assertEquals(2f, parse(input, JsonOps.INSTANCE).result().orElseThrow().get(65));
	}

	@Test void customKeyValueOrFieldCodecsRemainUntouchedIncludingDuplicateErrors() {
		Codec<Integer> duplicateKeys = Codec.STRING.xmap(ignored -> 65, Character::toString);
		UnboundedMapCodec<Integer, Float> duplicateSource = Codec.unboundedMap(duplicateKeys, Codec.FLOAT);
		MapCodec<Map<Integer, Float>> duplicates = duplicateSource.fieldOf("advances");
		assertSame(duplicates, SpaceAdvanceCodec.wrap(duplicateSource, "advances", duplicates));
		assertTrue(duplicates.codec().parse(JsonOps.INSTANCE, input(256)).error().orElseThrow().message().contains("Duplicate entry"));
		UnboundedMapCodec<Integer, Float> customValues = Codec.unboundedMap(ExtraCodecs.CODEPOINT,
			Codec.FLOAT.validate(ignored -> DataResult.error(() -> "custom value rejection")));
		MapCodec<Map<Integer, Float>> values = customValues.fieldOf("advances");
		assertSame(values, SpaceAdvanceCodec.wrap(customValues, "advances", values));
		MapCodec<Map<Integer, Float>> transformed = ORIGINAL.flatXmap(DataResult::success, DataResult::success);
		assertSame(transformed, SpaceAdvanceCodec.wrap(SOURCE, "advances", transformed));
		assertSame(ORIGINAL, SpaceAdvanceCodec.wrap(SOURCE, "another_field", ORIGINAL));
	}

	@Test void unknownNumberProviderRunsOriginalExactlyOnce() {
		AtomicInteger reads = new AtomicInteger();
		Number custom = new Number() {
			@Override public int intValue() { return 3; }
			@Override public long longValue() { return 3; }
			@Override public float floatValue() { reads.incrementAndGet(); return 3f; }
			@Override public double doubleValue() { return 3; }
		};
		JsonObject input = input(256);
		input.getAsJsonObject("advances").addProperty("A", custom);
		assertEquals(3f, parse(input, JsonOps.INSTANCE).result().orElseThrow().get(65));
		assertEquals(1, reads.get());
		assertEquals(0, coordinator.diagnostics().spaceCodecAdmitted());
	}

	@Test void customMapLikeIsNotReadSpeculativelyBeforeDelegating() {
		JsonObject source = input(256);
		AtomicInteger reads = new AtomicInteger();
		MapLike<JsonElement> custom = new MapLike<>() {
			@Override public JsonElement get(JsonElement key) { return get(key.getAsString()); }
			@Override public JsonElement get(String key) { reads.incrementAndGet(); return source.get(key); }
			@Override public Stream<Pair<JsonElement, JsonElement>> entries() { throw new AssertionError("Field decoding does not enumerate this custom owner"); }
		};
		AtomicReference<DataResult<Map<Integer, Float>>> result = new AtomicReference<>();
		coordinator.executor(Runnable::run).execute(() -> result.set(CANDIDATE.decode(JsonOps.INSTANCE, custom)));
		assertEquals(256, result.get().result().orElseThrow().size());
		assertEquals(1, reads.get());
		assertEquals(0, coordinator.diagnostics().spaceCodecAdmitted());
	}

	@Test void exhaustedBudgetAndRetiredScopeBypassWithoutRetainingScratch() {
		JsonObject input = input(256);
		long limit = context.features().optimizationMemoryMiB() * 1024L * 1024L;
		try (PreparationBudget.Reservation occupied = context.preparationBudget().tryReserve(limit - bookkeeping)) {
			assertEquivalent(ORIGINAL.codec().parse(JsonOps.INSTANCE, input), parse(input, JsonOps.INSTANCE));
			assertEquals(limit, context.preparationBudget().used());
			assertEquals(1, coordinator.diagnostics().spaceCodecBudgetBypasses());
		}
		assertEquivalent(ORIGINAL.codec().parse(JsonOps.INSTANCE, input), parse(input, JsonOps.INSTANCE));
		assertEquals(1, coordinator.diagnostics().spaceCodecAdmitted());
		context.preparationBudget().retire();
		assertEquivalent(ORIGINAL.codec().parse(JsonOps.INSTANCE, input), parse(input, JsonOps.INSTANCE));
		assertEquals(0, context.preparationBudget().used());
		assertEquals(1, coordinator.diagnostics().spaceCodecStaleBypasses());
	}

	@Test void initialDenialRetriesEssentialBookkeepingBeforeOptionalCodecAdmission() {
		coordinator.close(); ReloadExecutionContext.finish(context);
		context = ReloadExecutionContext.startForTesting(ReloadFeatureSnapshot.capture());
		PreparationBudget.Scope scope = context.preparationBudget();
		long limit = context.features().optimizationMemoryMiB() * 1024L * 1024L;
		try (PreparationBudget.Reservation occupied = scope.tryReserve(limit)) {
			coordinator = new FontPreparationCoordinator(scope, Set.of(), true);
			assertEquals(limit, scope.used(), "Constructor denial must not allocate bookkeeping beyond capacity");
		}
		JsonObject input = input(256);
		try (PreparationBudget.Reservation occupied = scope.tryReserve(limit - 8192)) {
			assertEquivalent(ORIGINAL.codec().parse(JsonOps.INSTANCE, input), parse(input, JsonOps.INSTANCE));
			assertEquals(limit, scope.used(), "Recovered 8 KiB belongs to essential bookkeeping before optional decode scratch");
			assertEquals(0, coordinator.diagnostics().spaceCodecAdmitted());
			assertEquals(1, coordinator.diagnostics().spaceCodecBudgetBypasses());
		}
		assertEquals(8192, scope.used());
		assertEquivalent(ORIGINAL.codec().parse(JsonOps.INSTANCE, input), parse(input, JsonOps.INSTANCE));
		assertEquals(1, coordinator.diagnostics().spaceCodecAdmitted());
		assertEquals(256, coordinator.diagnostics().spaceCodecAdmittedEntries());
		assertEquals(2, coordinator.diagnostics().spaceCodecCalls());
		assertEquals(8192, scope.used(), "Successful retry must not duplicate bookkeeping or retain decode scratch");
	}

	@Test void retirementDuringAdmittedDecodeKeepsScratchChargedAndRecordsTerminalBypass() throws Exception {
		CountDownLatch admitted = new CountDownLatch(1), resume = new CountDownLatch(1);
		MapCodec<Map<Integer, Float>> paused = SpaceAdvanceCodec.wrap(SOURCE, "advances", ORIGINAL, () -> {
			admitted.countDown();
			try { assertTrue(resume.await(5, TimeUnit.SECONDS)); }
			catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
		});
		JsonObject input = input(256);
		try (var executor = Executors.newSingleThreadExecutor()) {
			CompletableFuture<DataResult<Map<Integer, Float>>> running = CompletableFuture.supplyAsync(
				() -> paused.codec().parse(JsonOps.INSTANCE, input), coordinator.executor(executor));
			try {
				assertTrue(admitted.await(5, TimeUnit.SECONDS));
				assertTrue(context.preparationBudget().used() > bookkeeping);
				context.preparationBudget().retire();
				assertTrue(context.preparationBudget().used() > 0, "A retired scope cannot uncharge a still-running admitted decoder");
			} finally { resume.countDown(); }
			assertEquivalent(ORIGINAL.codec().parse(JsonOps.INSTANCE, input), running.get(5, TimeUnit.SECONDS));
			assertEquals(0, context.preparationBudget().used());
			assertEquals(1, coordinator.diagnostics().spaceCodecCalls());
			assertEquals(1, coordinator.diagnostics().spaceCodecStaleBypasses(), "Existing synchronized counters must record completion after coordinator closure");
			assertEquals(0, coordinator.diagnostics().spaceCodecAdmitted());
		}
	}

	@Test void parsingFailureReleasesScratchAndPreservesOriginalException() {
		JsonObject input = input(256);
		input.getAsJsonObject("advances").add("A", new JsonPrimitive(new LazilyParsedNumber("broken")));
		NumberFormatException expected = assertThrows(NumberFormatException.class, () -> ORIGINAL.codec().parse(JsonOps.INSTANCE, input));
		NumberFormatException actual = assertThrows(NumberFormatException.class, () -> parse(input, JsonOps.INSTANCE));
		assertEquals(expected.getMessage(), actual.getMessage());
		assertEquals(bookkeeping, context.preparationBudget().used());
	}

	@Test void concurrentDefinitionsUseIndependentMapsAndReturnAllScratchReservations() throws Exception {
		try (var executor = Executors.newFixedThreadPool(6)) {
			List<CompletableFuture<Map<Integer, Float>>> jobs = new ArrayList<>();
			for (int index = 0; index < 6; index++) {
				JsonObject input = input(1024);
				jobs.add(CompletableFuture.supplyAsync(() -> CANDIDATE.codec().parse(JsonOps.INSTANCE, input).result().orElseThrow(),
					coordinator.executor(executor)));
			}
			Map<Integer, Float> first = jobs.getFirst().get(5, TimeUnit.SECONDS);
			for (int index = 1; index < jobs.size(); index++) {
				Map<Integer, Float> next = jobs.get(index).get(5, TimeUnit.SECONDS);
				assertEquals(first, next); assertNotSame(first, next);
			}
			assertEquals(6, coordinator.diagnostics().spaceCodecAdmitted());
			assertEquals(6 * 1024, coordinator.diagnostics().spaceCodecAdmittedEntries());
			assertEquals(bookkeeping, context.preparationBudget().used());
		}
	}

	@Test void decodingOutsideFontPreparationDoesNotActivateShortcut() {
		JsonObject input = input(256);
		assertEquivalent(ORIGINAL.codec().parse(JsonOps.INSTANCE, input), CANDIDATE.codec().parse(JsonOps.INSTANCE, input));
		assertEquals(0, coordinator.diagnostics().spaceCodecCalls());
	}

	private DataResult<Map<Integer, Float>> parse(JsonElement input, JsonOps ops) {
		AtomicReference<DataResult<Map<Integer, Float>>> result = new AtomicReference<>();
		coordinator.executor(Runnable::run).execute(() -> result.set(CANDIDATE.codec().parse(ops, input)));
		return result.get();
	}

	private static JsonObject input(int entries) {
		JsonObject advances = new JsonObject();
		for (int index = 0; index < entries; index++) advances.addProperty(Character.toString(0x1000 + index), index % 29 - 3.25f);
		JsonObject input = new JsonObject(); input.add("advances", advances); return input;
	}

	private static void assertEquivalent(DataResult<Map<Integer, Float>> expected, DataResult<Map<Integer, Float>> actual) {
		assertEquals(expected.isSuccess(), actual.isSuccess());
		assertEquals(expected.lifecycle(), actual.lifecycle());
		assertEquals(expected.error().map(error -> error.message()), actual.error().map(error -> error.message()));
		assertEquals(expected.resultOrPartial(), actual.resultOrPartial());
		if (expected.resultOrPartial().isPresent()) {
			Map<Integer, Float> expectedValues = expected.resultOrPartial().orElseThrow();
			Map<Integer, Float> actualValues = actual.resultOrPartial().orElseThrow();
			assertEquals(List.copyOf(expectedValues.keySet()), List.copyOf(actualValues.keySet()));
			for (Map.Entry<Integer, Float> entry : expectedValues.entrySet()) {
				assertEquals(Float.floatToRawIntBits(entry.getValue()), Float.floatToRawIntBits(actualValues.get(entry.getKey())));
			}
		}
	}
}

