package de.Exitus.glowblock;

import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class GlowConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = "glowblock.json";
	private static final long SAVE_DEBOUNCE_MS = 500;

	private static volatile boolean dirty = false;
	private static long lastSaveTime = 0;

	private GlowConfig() {
	}

	private static Path getConfigPath() {
		return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
	}

	public static void markDirty() {
		dirty = true;
	}

	public static void tick() {
		if (dirty && System.currentTimeMillis() - lastSaveTime > SAVE_DEBOUNCE_MS) {
			saveNow();
		}
	}

	public static void saveNow() {
		dirty = false;
		lastSaveTime = System.currentTimeMillis();

		JsonObject root = new JsonObject();

		JsonArray tracked = new JsonArray();
		for (Block block : GlowState.getTrackedBlocks()) {
			Identifier id = BuiltInRegistries.BLOCK.getKey(block);
			if (id != null) tracked.add(id.toString());
		}
		root.add("tracked", tracked);

		JsonObject colors = new JsonObject();
		for (Map.Entry<String, GlowColorSpec> entry : GlowState.getAllSavedSpecs().entrySet()) {
			colors.add(entry.getKey(), entry.getValue().toJson());
		}
		root.add("colors", colors);

		JsonObject display = new JsonObject();
		display.addProperty("xray", GlowState.isXrayEnabled());
		display.addProperty("beams", GlowState.isBeamsEnabled());
		display.addProperty("fill", GlowState.isFillEnabled());
		root.add("display", display);

		try {
			Path path = getConfigPath();
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(root));
		} catch (IOException e) {
			GlowBlockMod.LOGGER.error("Failed to save config", e);
		}
	}

	public static void load() {
		Path path = getConfigPath();
		if (!Files.exists(path)) return;
		try {
			String json = Files.readString(path);
			JsonElement parsed = JsonParser.parseString(json);

			if (parsed.isJsonArray()) {
				loadLegacy(parsed.getAsJsonArray());
				return;
			}

			if (!parsed.isJsonObject()) return;
			JsonObject root = parsed.getAsJsonObject();

			if (root.has("colors")) {
				JsonObject colorsObj = root.getAsJsonObject("colors");
				for (Map.Entry<String, JsonElement> entry : colorsObj.entrySet()) {
					GlowColorSpec spec = GlowColorSpec.fromJson(entry.getValue());
					GlowState.putSavedSpec(entry.getKey(), spec);
				}
			}

			if (root.has("tracked")) {
				JsonArray trackedArr = root.getAsJsonArray("tracked");
				for (JsonElement elem : trackedArr) {
					String idStr = elem.getAsString();
					Identifier id = Identifier.tryParse(idStr);
					if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
						Block block = BuiltInRegistries.BLOCK.getValue(id);
						GlowState.addTargetWithSavedColor(block);
					}
				}
			}

			if (root.has("display") && root.get("display").isJsonObject()) {
				JsonObject display = root.getAsJsonObject("display");
				if (display.has("xray")) {
					GlowState.setXrayEnabled(display.get("xray").getAsBoolean());
				}
				if (display.has("beams")) {
					GlowState.setBeamsEnabled(display.get("beams").getAsBoolean());
				}
				if (display.has("fill")) {
					GlowState.setFillEnabled(display.get("fill").getAsBoolean());
				} else if (display.has("fillAlpha")) {
					GlowState.setFillEnabled(display.get("fillAlpha").getAsInt() > 0);
				}
			}
		} catch (Exception e) {
			GlowBlockMod.LOGGER.error("Failed to load config", e);
		}
	}

	private static void loadLegacy(JsonArray arr) {
		for (JsonElement elem : arr) {
			String idStr;
			int color;

			if (elem.isJsonPrimitive()) {
				idStr = elem.getAsString();
				Identifier id = Identifier.tryParse(idStr);
				if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
					Block block = BuiltInRegistries.BLOCK.getValue(id);
					GlowState.addTarget(block, GlowColors.forBlock(block));
				}
			} else if (elem.isJsonObject()) {
				JsonObject obj = elem.getAsJsonObject();
				idStr = obj.get("id").getAsString();
				String hexColor = obj.has("color") ? obj.get("color").getAsString() : null;

				Identifier id = Identifier.tryParse(idStr);
				if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
					Block block = BuiltInRegistries.BLOCK.getValue(id);
					if (hexColor != null) {
						try {
							color = (int) Long.parseUnsignedLong(hexColor, 16);
						} catch (NumberFormatException e) {
							color = GlowColors.forBlock(block);
						}
					} else {
						color = GlowColors.forBlock(block);
					}
					GlowState.addTarget(block, color);
				}
			}
		}
	}
}
