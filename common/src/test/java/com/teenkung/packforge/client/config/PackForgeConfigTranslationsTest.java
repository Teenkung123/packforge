package com.teenkung.packforge.client.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackForgeConfigTranslationsTest {
	@Test
	void everyConfigModelKeyHasEnglishText() throws Exception {
		JsonObject translations;
		try (var input = getClass().getClassLoader().getResourceAsStream("assets/packforge/lang/en_us.json")) {
			assertTrue(input != null, "English PackForge translations are missing");
			try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
				translations = JsonParser.parseReader(reader).getAsJsonObject();
			}
		}

		Set<String> required = new LinkedHashSet<>();
		required.addAll(List.of(
			"packforge.config.title",
			"packforge.config.search",
			"packforge.config.reset",
			"packforge.config.reset_all",
			"packforge.config.done",
			"packforge.config.cancel",
			"packforge.config.on",
			"packforge.config.off",
			"packforge.config.save_error",
			"packforge.config.empty",
			"packforge.config.button.reset",
			"packforge.config.button.reset.tooltip",
			"packforge.config.button.reset_all",
			"packforge.config.button.reset_all.tooltip",
			"packforge.config.button.done",
			"packforge.config.button.done.tooltip",
			"packforge.config.button.cancel.tooltip",
			"packforge.config.value.enabled",
			"packforge.config.value.disabled",
			"packforge.config.save_failed",
			"packforge.config.resource_pack_button"
		));
		for (PackForgeConfigScreenModel.Category category : PackForgeConfigScreenModel.Category.values()) {
			required.add(category.translationKey());
		}
		for (PackForgeConfigScreenModel.ApplyScope scope : PackForgeConfigScreenModel.ApplyScope.values()) {
			required.add(scope.translationKey());
		}
		for (PackForgeConfigScreenModel.OptionSpec option : PackForgeConfigScreenModel.allOptions()) {
			required.add(option.sectionKey());
			required.add(option.titleKey());
			required.add(option.descriptionKey());
		}

		Set<String> missing = new LinkedHashSet<>();
		for (String key : required) {
			if (!translations.has(key) || translations.get(key).getAsString().isBlank()) {
				missing.add(key);
			}
		}
		assertTrue(missing.isEmpty(), "Missing PackForge config translations: " + missing);
	}

	@Test
	void configCogIsACompactWhiteTransparentSprite() throws Exception {
		try (var input = getClass().getClassLoader()
			.getResourceAsStream("assets/packforge/textures/gui/sprites/config_cog.png")) {
			assertNotNull(input, "PackForge config cog sprite is missing");
			var image = ImageIO.read(input);
			assertNotNull(image, "PackForge config cog sprite is not a readable PNG");
			assertEquals(16, image.getWidth());
			assertEquals(16, image.getHeight());
			int opaqueWhite = 0;
			int transparent = 0;
			for (int y = 0; y < image.getHeight(); y++) {
				for (int x = 0; x < image.getWidth(); x++) {
					int argb = image.getRGB(x, y);
					int alpha = argb >>> 24;
					if (alpha == 0) {
						transparent++;
					} else {
						assertEquals(0xFFFFFFFF, argb, "Cog pixels must be pure opaque white");
						opaqueWhite++;
					}
				}
			}
			assertTrue(opaqueWhite >= 64, "Cog must remain legible at native scale");
			assertTrue(transparent >= 64, "Cog must retain a transparent background");
		}
	}
}
