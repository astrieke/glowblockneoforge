package de.Exitus.glowblock;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.Map;

public final class GlowColors {

	public static final int DEFAULT = 0xFFD500FF;

	private static final Map<String, Integer> BY_PATH = new HashMap<>();
	static {
		BY_PATH.put("gold_ore",           0xFFFFD700);
		BY_PATH.put("deepslate_gold_ore", 0xFFFFD700);
		BY_PATH.put("nether_gold_ore",    0xFFFFD700);
		BY_PATH.put("raw_gold_block",     0xFFFFD700);
		BY_PATH.put("gold_block",         0xFFFFD700);

		BY_PATH.put("diamond_ore",           0xFF55FFFF);
		BY_PATH.put("deepslate_diamond_ore", 0xFF55FFFF);
		BY_PATH.put("diamond_block",         0xFF55FFFF);

		BY_PATH.put("emerald_ore",           0xFF55FF55);
		BY_PATH.put("deepslate_emerald_ore", 0xFF55FF55);
		BY_PATH.put("emerald_block",         0xFF55FF55);

		BY_PATH.put("iron_ore",           0xFFDDDDDD);
		BY_PATH.put("deepslate_iron_ore", 0xFFDDDDDD);
		BY_PATH.put("raw_iron_block",     0xFFDDDDDD);
		BY_PATH.put("iron_block",         0xFFDDDDDD);

		BY_PATH.put("copper_ore",           0xFFCC7733);
		BY_PATH.put("deepslate_copper_ore", 0xFFCC7733);
		BY_PATH.put("raw_copper_block",     0xFFCC7733);
		BY_PATH.put("copper_block",         0xFFCC7733);

		BY_PATH.put("redstone_ore",           0xFFFF3333);
		BY_PATH.put("deepslate_redstone_ore", 0xFFFF3333);
		BY_PATH.put("redstone_block",         0xFFFF3333);

		BY_PATH.put("lapis_ore",           0xFF3355FF);
		BY_PATH.put("deepslate_lapis_ore", 0xFF3355FF);
		BY_PATH.put("lapis_block",         0xFF3355FF);

		BY_PATH.put("coal_ore",           0xFF444444);
		BY_PATH.put("deepslate_coal_ore", 0xFF444444);
		BY_PATH.put("coal_block",         0xFF444444);

		BY_PATH.put("ancient_debris", 0xFFAA3311);
		BY_PATH.put("netherite_block",0xFFAA3311);
	}

	private GlowColors() {
	}

	public static int forBlock(Block block) {
		Identifier id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
		if (id == null) return DEFAULT;
		Integer c = BY_PATH.get(id.getPath());
		return c == null ? DEFAULT : c;
	}
}
