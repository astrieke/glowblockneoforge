package de.Exitus.glowblock;

import com.google.gson.*;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class GlowPreset {
	private String name;
	private final Map<String, GlowColorSpec> blocks = new LinkedHashMap<>();

	public GlowPreset(String name) {
		this.name = name;
	}

	public String getName() { return name; }

	public Map<String, GlowColorSpec> getBlocks() { return blocks; }

	public void putBlock(String blockId, GlowColorSpec spec) {
		blocks.put(blockId, spec != null ? spec : GlowColorSpec.solid(GlowColors.DEFAULT));
	}

	public void removeBlock(String blockId) { blocks.remove(blockId); }
	public boolean hasBlock(String blockId) { return blocks.containsKey(blockId); }

	public GlowColorSpec getSpec(String blockId) {
		GlowColorSpec spec = blocks.get(blockId);
		return spec != null ? spec : GlowColorSpec.solid(GlowColors.DEFAULT);
	}

	private static Path getPresetsDir() {
		return FMLPaths.CONFIGDIR.get().resolve("glowblock").resolve("presets");
	}

	private static String sanitizeFilename(String name) {
		return name.replaceAll("[^a-zA-Z0-9_\\-äöüÄÖÜß ]", "").trim();
	}

	public void save() {
		try {
			Path dir = getPresetsDir();
			Files.createDirectories(dir);
			JsonObject root = new JsonObject();
			for (Map.Entry<String, GlowColorSpec> e : blocks.entrySet()) {
				root.add(e.getKey(), e.getValue().toJson());
			}
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			Path file = dir.resolve(sanitizeFilename(name) + ".json");
			Files.writeString(file, gson.toJson(root));
		} catch (IOException e) {
			GlowBlockMod.LOGGER.error("Failed to save preset '{}'", name, e);
		}
	}

	public void deleteFile() {
		try {
			Path file = getPresetsDir().resolve(sanitizeFilename(name) + ".json");
			Files.deleteIfExists(file);
		} catch (IOException e) {
			GlowBlockMod.LOGGER.error("Failed to delete preset '{}'", name, e);
		}
	}

	public boolean rename(String newName) {
		String oldSafe = sanitizeFilename(name);
		String newSafe = sanitizeFilename(newName);
		if (newSafe.isEmpty()) return false;
		try {
			Path dir = getPresetsDir();
			Path oldFile = dir.resolve(oldSafe + ".json");
			Path newFile = dir.resolve(newSafe + ".json");
			if (Files.exists(newFile)) return false;
			if (Files.exists(oldFile)) {
				Files.move(oldFile, newFile);
			}
			this.name = newName;
			return true;
		} catch (IOException e) {
			GlowBlockMod.LOGGER.error("Failed to rename preset '{}' to '{}'", name, newName, e);
			return false;
		}
	}

	public static GlowPreset load(String name) {
		GlowPreset preset = new GlowPreset(name);
		try {
			Path file = getPresetsDir().resolve(sanitizeFilename(name) + ".json");
			if (!Files.exists(file)) return preset;
			String json = Files.readString(file);
			JsonObject root = JsonParser.parseString(json).getAsJsonObject();
			for (Map.Entry<String, JsonElement> e : root.entrySet()) {
				preset.blocks.put(e.getKey(), GlowColorSpec.fromJson(e.getValue()));
			}
		} catch (Exception e) {
			GlowBlockMod.LOGGER.error("Failed to load preset '{}'", name, e);
		}
		return preset;
	}

	public static List<String> listAll() {
		List<String> names = new ArrayList<>();
		try {
			Path dir = getPresetsDir();
			if (!Files.exists(dir)) return names;
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
				for (Path p : stream) {
					String fileName = p.getFileName().toString();
					names.add(fileName.substring(0, fileName.length() - 5));
				}
			}
		} catch (IOException e) {
			GlowBlockMod.LOGGER.error("Failed to list presets", e);
		}
		Collections.sort(names);
		return names;
	}

	public static GlowPreset fromCurrentState(String name) {
		GlowPreset preset = new GlowPreset(name);
		for (var block : GlowState.getTrackedBlocks()) {
			var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
			if (id != null) {
				preset.blocks.put(id.toString(), GlowState.getSpec(block).copy());
			}
		}
		return preset;
	}
}
