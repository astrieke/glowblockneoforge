package de.Exitus.glowblock;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;

public final class GlowInvalidation {
	private static final int VERIFY_INTERVAL_TICKS = 5;
	private static final int VERIFY_BUDGET = 512;

	private static final double VERIFY_RADIUS_SQ = 68.0 * 68.0;

	private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
	private static final LongArrayList toRemove = new LongArrayList();

	private static int tickCounter = 0;

	private GlowInvalidation() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(GlowInvalidation::onClientTick);
	}

	private static void onClientTick(ClientTickEvent.Post event) {
		Minecraft client = Minecraft.getInstance();
		tickCounter++;
		if (tickCounter % VERIFY_INTERVAL_TICKS != 0) return;

		Level level = client.level;
		if (level == null || client.player == null) return;
		if (!GlowState.hasAnyTarget()) return;

		double px = client.player.getX();
		double py = client.player.getY();
		double pz = client.player.getZ();

		int budget = VERIFY_BUDGET;

		for (Block block : GlowState.getTrackedBlocks()) {
			if (budget <= 0) break;

			LongSet positions = GlowState.getSeenPositions(block);
			if (positions.isEmpty()) continue;

			toRemove.clear();
			LongIterator it = positions.iterator();

			while (it.hasNext() && budget > 0) {
				long packed = it.nextLong();
				CURSOR.set(packed);

				double dx = CURSOR.getX() + 0.5 - px;
				double dy = CURSOR.getY() + 0.5 - py;
				double dz = CURSOR.getZ() + 0.5 - pz;
				if (dx * dx + dy * dy + dz * dz > VERIFY_RADIUS_SQ) continue;

				budget--;

				if (!level.hasChunkAt(CURSOR.getX(), CURSOR.getZ())) continue;

				if (level.getBlockState(CURSOR).getBlock() != block) {
					toRemove.add(packed);
				}
			}

			for (int i = 0; i < toRemove.size(); i++) {
				GlowState.removeSeen(block, toRemove.getLong(i));
			}
		}
	}
}
