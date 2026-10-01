package de.Exitus.glowblock;

import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class GlowRenderer {
	private static final double MAX_RENDER_DISTANCE = 64.0;
	private static final double MAX_RENDER_DISTANCE_SQ = MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE;

	private static final double BEAM_MIN_DISTANCE = 12.0;
	private static final double BEAM_MAX_DISTANCE = 20.0;
	private static final double BEAM_HEIGHT = 30.0;
	private static final float BEAM_WIDTH = 3.0f;
	private static final double FADE_START_DISTANCE = 52.0;
	private static final int EMIT_EVERY_TICKS = 2;
	private static final int GIZMO_LIFETIME_MS = 500;
	private static final int STROKE_ALPHA = 255;
	private static final int FILL_ALPHA = 10;
	private static final int BEAM_ALPHA = 150;
	private static final float STROKE_WIDTH = 2.5f;
	private static final float PULSE_AMOUNT = 0.0f;
	private static final float PULSE_SPEED = 0.05f;
	private static int tickCounter = 0;

	private GlowRenderer() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(GlowRenderer::onClientTick);
	}

	private static void onClientTick(ClientTickEvent.Post event) {
		Minecraft client = Minecraft.getInstance();
		tickCounter++;
		if (tickCounter % EMIT_EVERY_TICKS != 0) return;

		if (!GlowState.hasAnyTarget()) return;

		Level level = client.level;
		if (level == null || client.player == null) return;

		Vec3 eye = client.gameRenderer.mainCamera().position();

		float pulse = PULSE_AMOUNT <= 0.0f
				? 1.0f
				: 1.0f + PULSE_AMOUNT * Mth.sin(level.getGameTime() * PULSE_SPEED);

		boolean xray = GlowState.isXrayEnabled();
		boolean beams = GlowState.isBeamsEnabled();
		boolean fill = GlowState.isFillEnabled();

		float timeSeconds = level.getGameTime() / 20.0f;

		for (Block block : GlowState.getTrackedBlocks()) {
			GlowColorSpec spec = GlowState.getSpec(block);
			int rgb = spec.currentColor(timeSeconds) & 0x00FFFFFF;

			for (GlowState.Cluster cluster : GlowState.getClusters(block)) {
				double dx = cluster.centerX - eye.x;
				double dy = cluster.centerY - eye.y;
				double dz = cluster.centerZ - eye.z;
				double distSq = dx * dx + dy * dy + dz * dz;
				if (distSq > MAX_RENDER_DISTANCE_SQ) continue;

				if (!validateCluster(level, block, cluster)) continue;

				double dist = Math.sqrt(distSq);

				float distFade = 1.0f;
				if (dist > FADE_START_DISTANCE) {
					distFade = (float) (1.0 - (dist - FADE_START_DISTANCE)
							/ (MAX_RENDER_DISTANCE - FADE_START_DISTANCE));
					distFade = Mth.clamp(distFade, 0.0f, 1.0f);
				}

				float alphaScale = Mth.clamp(distFade * pulse, 0.0f, 1.0f);

				int strokeArgb = argb(STROKE_ALPHA, alphaScale, rgb);
				int fillArgb = fill ? argb(FILL_ALPHA, alphaScale, rgb) : 0;

				int width = cluster.maxX - cluster.minX + 1;
				int height = cluster.maxY - cluster.minY + 1;
				int depth = cluster.maxZ - cluster.minZ + 1;
				boolean isFullBox = cluster.count == width * height * depth;

				if (isFullBox) {
					double x1 = cluster.minX;
					double y1 = cluster.minY;
					double z1 = cluster.minZ;
					double x2 = cluster.maxX + 1;
					double y2 = cluster.maxY + 1;
					double z2 = cluster.maxZ + 1;

					drawClusterOutline(x1, y1, z1, x2, y2, z2, strokeArgb, xray);

					if (fill) {
						AABB box = new AABB(x1, y1, z1, x2, y2, z2);
						var fillBox = Gizmos.cuboid(box, GizmoStyle.strokeAndFill(0, 0.0f, fillArgb));
						if (xray) fillBox.setAlwaysOnTop();
						fillBox.persistForMillis(GIZMO_LIFETIME_MS);
					}
				} else {
					drawPerBlockOutline(cluster, strokeArgb, xray, fill, fillArgb);
				}

				if (beams && dist > BEAM_MIN_DISTANCE) {
					float beamFade = (float) Math.min(1.0,
							(dist - BEAM_MIN_DISTANCE) / (BEAM_MAX_DISTANCE - BEAM_MIN_DISTANCE));

					int beamArgb = argb(BEAM_ALPHA, beamFade * distFade, rgb);

					var beam = Gizmos.line(
							new Vec3(cluster.beamX, cluster.beamY, cluster.beamZ),
							new Vec3(cluster.beamX, cluster.beamY + BEAM_HEIGHT, cluster.beamZ),
							beamArgb,
							BEAM_WIDTH
					);
					if (xray) beam.setAlwaysOnTop();
					beam.persistForMillis(GIZMO_LIFETIME_MS);
				}
			}
		}
	}

	private static final net.minecraft.core.BlockPos.MutableBlockPos VALIDATE_CURSOR =
			new net.minecraft.core.BlockPos.MutableBlockPos();

	private static boolean validateCluster(Level level, Block block, GlowState.Cluster cluster) {
		boolean anyValid = false;
		boolean anyStale = false;

		var it = cluster.positions.iterator();
		while (it.hasNext()) {
			long packed = it.nextLong();
			VALIDATE_CURSOR.set(packed);

			if (!level.hasChunkAt(VALIDATE_CURSOR.getX(), VALIDATE_CURSOR.getZ())) {
				anyValid = true;
				continue;
			}

			if (level.getBlockState(VALIDATE_CURSOR).getBlock() == block) {
				anyValid = true;
			} else {
				anyStale = true;
				GlowState.removeSeen(block, packed);
			}
		}

		return anyValid && !anyStale;
	}

	private static void drawPerBlockOutline(GlowState.Cluster cluster, int strokeArgb,
											boolean xray, boolean fill, int fillArgb) {
		var it = cluster.positions.iterator();
		while (it.hasNext()) {
			long packed = it.nextLong();
			int bx = net.minecraft.core.BlockPos.getX(packed);
			int by = net.minecraft.core.BlockPos.getY(packed);
			int bz = net.minecraft.core.BlockPos.getZ(packed);

			double x2 = bx + 1, y2 = by + 1, z2 = bz + 1;

			boolean hasWest  = cluster.positions.contains(net.minecraft.core.BlockPos.asLong(bx - 1, by, bz));
			boolean hasEast  = cluster.positions.contains(net.minecraft.core.BlockPos.asLong(bx + 1, by, bz));
			boolean hasDown  = cluster.positions.contains(net.minecraft.core.BlockPos.asLong(bx, by - 1, bz));
			boolean hasUp    = cluster.positions.contains(net.minecraft.core.BlockPos.asLong(bx, by + 1, bz));
			boolean hasNorth = cluster.positions.contains(net.minecraft.core.BlockPos.asLong(bx, by, bz - 1));
			boolean hasSouth = cluster.positions.contains(net.minecraft.core.BlockPos.asLong(bx, by, bz + 1));

			Vec3 c000 = new Vec3(bx, by, bz);
			Vec3 c100 = new Vec3(x2, by, bz);
			Vec3 c101 = new Vec3(x2, by, z2);
			Vec3 c001 = new Vec3(bx, by, z2);
			Vec3 c010 = new Vec3(bx, y2, bz);
			Vec3 c110 = new Vec3(x2, y2, bz);
			Vec3 c111 = new Vec3(x2, y2, z2);
			Vec3 c011 = new Vec3(bx, y2, z2);

			if (!hasDown || !hasNorth) emitLine(c000, c100, strokeArgb, xray);
			if (!hasDown || !hasEast)  emitLine(c100, c101, strokeArgb, xray);
			if (!hasDown || !hasSouth) emitLine(c101, c001, strokeArgb, xray);
			if (!hasDown || !hasWest)  emitLine(c001, c000, strokeArgb, xray);

			if (!hasUp || !hasNorth) emitLine(c010, c110, strokeArgb, xray);
			if (!hasUp || !hasEast)  emitLine(c110, c111, strokeArgb, xray);
			if (!hasUp || !hasSouth) emitLine(c111, c011, strokeArgb, xray);
			if (!hasUp || !hasWest)  emitLine(c011, c010, strokeArgb, xray);

			if (!hasWest || !hasNorth) emitLine(c000, c010, strokeArgb, xray);
			if (!hasEast || !hasNorth) emitLine(c100, c110, strokeArgb, xray);
			if (!hasEast || !hasSouth) emitLine(c101, c111, strokeArgb, xray);
			if (!hasWest || !hasSouth) emitLine(c001, c011, strokeArgb, xray);

			if (fill) {
				AABB box = new AABB(bx, by, bz, x2, y2, z2);
				var fillBox = Gizmos.cuboid(box, GizmoStyle.strokeAndFill(0, 0.0f, fillArgb));
				if (xray) fillBox.setAlwaysOnTop();
				fillBox.persistForMillis(GIZMO_LIFETIME_MS);
			}
		}
	}

	private static void drawClusterOutline(double x1, double y1, double z1,
										   double x2, double y2, double z2,
										   int argb, boolean xray) {
		Vec3 c000 = new Vec3(x1, y1, z1);
		Vec3 c100 = new Vec3(x2, y1, z1);
		Vec3 c101 = new Vec3(x2, y1, z2);
		Vec3 c001 = new Vec3(x1, y1, z2);
		Vec3 c010 = new Vec3(x1, y2, z1);
		Vec3 c110 = new Vec3(x2, y2, z1);
		Vec3 c111 = new Vec3(x2, y2, z2);
		Vec3 c011 = new Vec3(x1, y2, z2);

		emitLine(c000, c100, argb, xray);
		emitLine(c100, c101, argb, xray);
		emitLine(c101, c001, argb, xray);
		emitLine(c001, c000, argb, xray);

		emitLine(c010, c110, argb, xray);
		emitLine(c110, c111, argb, xray);
		emitLine(c111, c011, argb, xray);
		emitLine(c011, c010, argb, xray);

		emitLine(c000, c010, argb, xray);
		emitLine(c100, c110, argb, xray);
		emitLine(c101, c111, argb, xray);
		emitLine(c001, c011, argb, xray);
	}

	private static void emitLine(Vec3 a, Vec3 b, int argb, boolean xray) {
		var line = Gizmos.line(a, b, argb, STROKE_WIDTH);
		if (xray) line.setAlwaysOnTop();
		line.persistForMillis(GIZMO_LIFETIME_MS);
	}

	private static int argb(int baseAlpha, float scale, int rgb) {
		int a = Mth.clamp((int) (baseAlpha * scale), 0, 255);
		return (a << 24) | (rgb & 0x00FFFFFF);
	}
}
