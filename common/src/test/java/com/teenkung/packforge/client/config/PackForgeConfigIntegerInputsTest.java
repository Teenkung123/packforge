package com.teenkung.packforge.client.config;

import com.teenkung.packforge.config.PackForgeConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackForgeConfigIntegerInputsTest {
	@Test
	void acceptsMinimumAndMaximumValues() {
		PackForgeConfigIntegerInputs inputs = new PackForgeConfigIntegerInputs();
		PackForgeConfig.Cfg config = new PackForgeConfig.Cfg();
		PackForgeConfigScreenModel.IntegerOption option = integerOption("atlas_retry_attempts");

		assertTrue(inputs.update(option, config, "1"));
		assertEquals(1, config.atlasRetryMaxAttempts);
		assertTrue(inputs.update(option, config, "10"));
		assertEquals(10, config.atlasRetryMaxAttempts);
		assertFalse(inputs.hasInvalid());
	}

	@Test
	void invalidBlankTextAndOutOfRangeValuesDoNotMutateDraft() {
		PackForgeConfigIntegerInputs inputs = new PackForgeConfigIntegerInputs();
		PackForgeConfig.Cfg config = new PackForgeConfig.Cfg();
		PackForgeConfigScreenModel.IntegerOption option = integerOption("atlas_retry_attempts");
		config.atlasRetryMaxAttempts = 7;

		for (String invalid : new String[] { "", "not-a-number", "0", "11" }) {
			assertFalse(inputs.update(option, config, invalid), invalid);
			assertEquals(7, config.atlasRetryMaxAttempts, invalid);
			assertTrue(inputs.isInvalid(option), invalid);
		}
	}

	@Test
	void correctionClearsInvalidState() {
		PackForgeConfigIntegerInputs inputs = new PackForgeConfigIntegerInputs();
		PackForgeConfig.Cfg config = new PackForgeConfig.Cfg();
		PackForgeConfigScreenModel.IntegerOption option = integerOption("atlas_retry_attempts");

		assertFalse(inputs.update(option, config, "invalid"));
		assertTrue(inputs.hasInvalid());
		assertTrue(inputs.update(option, config, "4"));

		assertEquals(4, config.atlasRetryMaxAttempts);
		assertFalse(inputs.hasInvalid());
	}

	@Test
	void resetAndClearDiscardTrackedInvalidInputs() {
		PackForgeConfigIntegerInputs inputs = new PackForgeConfigIntegerInputs();
		PackForgeConfig.Cfg config = new PackForgeConfig.Cfg();
		PackForgeConfigScreenModel.IntegerOption retries = integerOption("atlas_retry_attempts");
		PackForgeConfigScreenModel.IntegerOption batchSize = integerOption("model_parse_batch_size");

		assertFalse(inputs.update(retries, config, "0"));
		assertFalse(inputs.update(batchSize, config, "not-a-number"));
		inputs.reset(retries);

		assertFalse(inputs.isInvalid(retries));
		assertTrue(inputs.isInvalid(batchSize));
		assertTrue(inputs.hasInvalid());
		inputs.clear();
		assertFalse(inputs.hasInvalid());
	}

	@Test
	void tracksMultipleOptionIdsIndependently() {
		PackForgeConfigIntegerInputs inputs = new PackForgeConfigIntegerInputs();
		PackForgeConfig.Cfg config = new PackForgeConfig.Cfg();
		PackForgeConfigScreenModel.IntegerOption retries = integerOption("atlas_retry_attempts");
		PackForgeConfigScreenModel.IntegerOption batchSize = integerOption("model_parse_batch_size");

		assertFalse(inputs.update(retries, config, "0"));
		assertFalse(inputs.update(batchSize, config, "1"));
		assertTrue(inputs.isInvalid(retries));
		assertTrue(inputs.isInvalid(batchSize));
		assertTrue(inputs.update(retries, config, "3"));

		assertFalse(inputs.isInvalid(retries));
		assertTrue(inputs.isInvalid(batchSize));
		assertTrue(inputs.hasInvalid());
	}

	private static PackForgeConfigScreenModel.IntegerOption integerOption(String id) {
		return (PackForgeConfigScreenModel.IntegerOption) PackForgeConfigScreenModel.allOptions().stream()
			.filter(option -> option.id().equals(id))
			.findFirst()
			.orElseThrow();
	}
}
