package de.Exitus.glowblock;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class GlowVision {
	private static final int SCAN_RADIUS = 32;
	private static final int SCAN_RADIUS_SQ = SCAN_RADIUS * SCAN_RADIUS;
	private static final double COS_HALF_FOV = 0.30;
	private static final int SCAN_INTERVAL_TICKS = 4;
	private static final int RAYCAST_BUDGET_PER_TICK = 40;
	private static final int MAX_QUEUE_SIZE = 4096;
	private static final int MAX_PROBES_PER_BLOCK = 6;
	private static final int FAILURE_COOLDOWN_TICKS = 20;
	private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
	private static final LongArrayList pendingRaycasts = new LongArrayList();
	private static final LongOpenHashSet pendingSet = new LongOpenHashSet();
	private static final Long2LongOpenHashMap failureCooldown = new Long2LongOpenHashMap();
	private static int tickCounter = 0;
	private static boolean debug = false;
	private static int dbgCandidates = 0;
	private static int dbgAccepted = 0;
	private static int dbgRejected = 0;
	private static int dbgBuried = 0;

	private GlowVision() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(GlowVision::onClientTick);
	}

	public static void toggleDebug() {
		debug = !debug;
		dbgCandidates = dbgAccepted = dbgRejected = dbgBuried = 0;
	}

	private static void onClientTick(ClientTickEvent.Post event) {
		Minecraft client = Minecraft.getInstance();
		Level level = client.level;
		if (level == null || client.player == null) {
			invalidateQueue();
			return;
		}

		GlowState.checkWorldChange(level.dimension());

		if (!GlowState.hasAnyTarget()) {
			invalidateQueue();
			return;
		}

		tickCounter++;

		Vec3 eyePos = client.player.getEyePosition(1.0f);
		Vec3 lookDir = client.player.getViewVector(1.0f);

		if (tickCounter % SCAN_INTERVAL_TICKS == 0) {
			cleanupCooldowns();

			int before = pendingRaycasts.size();
			collectCandidates(level, client.player.blockPosition(), eyePos, lookDir);
			int added = pendingRaycasts.size() - before;

			if (debug && added > 0) {
				dbgCandidates += added;
				GlowBlockMod.LOGGER.info("[GLOW/scan] +{} Kandidaten, Queue={}",
						added, pendingRaycasts.size());
			}
		}

		processRaycastQueue(client, level, eyePos);

		if (debug && tickCounter % 100 == 0) {
			GlowBlockMod.LOGGER.info(
					"[GLOW/stats] Kandidaten={} Sichtbar={} Verdeckt={} Eingeschlossen={} | Gesehen={}",
					dbgCandidates, dbgAccepted, dbgRejected, dbgBuried, GlowState.totalSeen());
		}
	}

	private static void collectCandidates(Level level, BlockPos playerPos,
	                                      Vec3 eyePos, Vec3 lookDir) {
		int minX = playerPos.getX() - SCAN_RADIUS;
		int maxX = playerPos.getX() + SCAN_RADIUS;
		int minY = Math.max(level.getMinY(), playerPos.getY() - SCAN_RADIUS);
		int maxY = Math.min(level.getMaxY(), playerPos.getY() + SCAN_RADIUS);
		int minZ = playerPos.getZ() - SCAN_RADIUS;
		int maxZ = playerPos.getZ() + SCAN_RADIUS;

		int minChunkX = SectionPos.blockToSectionCoord(minX);
		int maxChunkX = SectionPos.blockToSectionCoord(maxX);
		int minChunkZ = SectionPos.blockToSectionCoord(minZ);
		int maxChunkZ = SectionPos.blockToSectionCoord(maxZ);
		int minSectionY = SectionPos.blockToSectionCoord(minY);
		int maxSectionY = SectionPos.blockToSectionCoord(maxY);

		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				ChunkAccess chunk = level.getChunkSource().getChunk(chunkX, chunkZ, false);
				if (chunk == null) continue;

				LevelChunkSection[] sections = chunk.getSections();

				for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
					int idx = chunk.getSectionIndexFromSectionY(sectionY);
					if (idx < 0 || idx >= sections.length) continue;

					LevelChunkSection section = sections[idx];
					if (section == null || section.hasOnlyAir()) continue;

					if (!section.maybeHas(s -> GlowState.isTracked(s.getBlock()))) continue;

					scanSection(level, section, chunkX, sectionY, chunkZ,
							minX, maxX, minY, maxY, minZ, maxZ,
							playerPos, eyePos, lookDir);
				}
			}
		}
	}

	private static void scanSection(Level level, LevelChunkSection section,
	                                int chunkX, int sectionY, int chunkZ,
	                                int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
	                                BlockPos playerPos, Vec3 eyePos, Vec3 lookDir) {
		int baseX = SectionPos.sectionToBlockCoord(chunkX);
		int baseY = SectionPos.sectionToBlockCoord(sectionY);
		int baseZ = SectionPos.sectionToBlockCoord(chunkZ);

		int loX = Math.max(0, minX - baseX), hiX = Math.min(15, maxX - baseX);
		int loY = Math.max(0, minY - baseY), hiY = Math.min(15, maxY - baseY);
		int loZ = Math.max(0, minZ - baseZ), hiZ = Math.min(15, maxZ - baseZ);

		for (int y = loY; y <= hiY; y++) {
			int worldY = baseY + y;
			int dy = worldY - playerPos.getY();

			for (int z = loZ; z <= hiZ; z++) {
				int worldZ = baseZ + z;
				int dz = worldZ - playerPos.getZ();

				for (int x = loX; x <= hiX; x++) {
					int worldX = baseX + x;
					int dx = worldX - playerPos.getX();

					if (dx * dx + dy * dy + dz * dz > SCAN_RADIUS_SQ) continue;

					BlockState state = section.getBlockState(x, y, z);
					Block block = state.getBlock();
					if (!GlowState.isTracked(block)) continue;

					long packed = BlockPos.asLong(worldX, worldY, worldZ);

					if (GlowState.isSeen(block, packed)) continue;
					if (pendingSet.contains(packed)) continue;
					if (isOnCooldown(packed)) continue;

					if (!isExposedToAir(level, worldX, worldY, worldZ)) {
						dbgBuried++;
						failureCooldown.put(packed, tickCounter + FAILURE_COOLDOWN_TICKS * 5L);
						continue;
					}

					double cx = worldX + 0.5, cy = worldY + 0.5, cz = worldZ + 0.5;
					double vx = cx - eyePos.x, vy = cy - eyePos.y, vz = cz - eyePos.z;
					double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
					if (len < 1.0e-4) continue;

					double dot = (vx * lookDir.x + vy * lookDir.y + vz * lookDir.z) / len;
					if (dot < COS_HALF_FOV) continue;

					if (pendingRaycasts.size() >= MAX_QUEUE_SIZE) {
						if (debug) {
							GlowBlockMod.LOGGER.warn("[GLOW/scan] Queue voll ({}), Rest übersprungen",
									MAX_QUEUE_SIZE);
						}
						return;
					}

					pendingRaycasts.add(packed);
					pendingSet.add(packed);
				}
			}
		}
	}

	private static boolean isExposedToAir(Level level, int x, int y, int z) {
		BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
		for (Direction dir : Direction.values()) {
			int nx = x + dir.getStepX();
			int ny = y + dir.getStepY();
			int nz = z + dir.getStepZ();

			if (ny < level.getMinY() || ny > level.getMaxY()) continue;

			probe.set(nx, ny, nz);
			BlockState neighbor = level.getBlockState(probe);

			if (!neighbor.getFluidState().isEmpty()) continue;

			if (!neighbor.isSolidRender()) {
				return true;
			}
		}
		return false;
	}

	private static void processRaycastQueue(Minecraft client, Level level, Vec3 eyePos) {
		int budget = Math.min(RAYCAST_BUDGET_PER_TICK, pendingRaycasts.size());

		for (int i = 0; i < budget; i++) {
			long packed = pendingRaycasts.removeLong(0);
			pendingSet.remove(packed);

			CURSOR.set(packed);

			BlockState state = level.getBlockState(CURSOR);
			Block block = state.getBlock();
			if (!GlowState.isTracked(block)) continue;
			if (GlowState.isSeen(block, packed)) continue;

			boolean visible = checkVisibility(client, level, eyePos, CURSOR, packed);

			if (debug) {
				GlowBlockMod.LOGGER.info("[GLOW/ray] {} @ {},{},{} | dist={} | {}",
						blockName(block), CURSOR.getX(), CURSOR.getY(), CURSOR.getZ(),
						String.format("%.1f", Math.sqrt(eyePos.distanceToSqr(Vec3.atCenterOf(CURSOR)))),
						visible ? "SICHTBAR" : "VERDECKT");
			}

			if (visible) {
				GlowState.markSeen(block, packed);
				dbgAccepted++;
			} else {
				dbgRejected++;
				failureCooldown.put(packed, tickCounter + FAILURE_COOLDOWN_TICKS);
			}
		}
	}

	private static boolean checkVisibility(Minecraft client, Level level,
	                                       Vec3 eyePos, BlockPos pos, long packed) {
		Vec3 center = Vec3.atCenterOf(pos);
		if (probe(client, level, eyePos, center, packed)) return true;

		BlockPos.MutableBlockPos probePos = new BlockPos.MutableBlockPos();
		int probes = 1;
		for (Direction dir : Direction.values()) {
			if (probes >= MAX_PROBES_PER_BLOCK) break;

			double nx = dir.getStepX(), ny = dir.getStepY(), nz = dir.getStepZ();

			double ex = eyePos.x - center.x;
			double ey = eyePos.y - center.y;
			double ez = eyePos.z - center.z;
			if (nx * ex + ny * ey + nz * ez <= 0) continue;

			probePos.set(pos.getX() + dir.getStepX(),
					pos.getY() + dir.getStepY(),
					pos.getZ() + dir.getStepZ());
			if (level.getBlockState(probePos).isSolidRender()) continue;
			Vec3 face = new Vec3(
					center.x + nx * 0.51,
					center.y + ny * 0.51,
					center.z + nz * 0.51
			);

			probes++;
			if (probe(client, level, eyePos, face, packed)) return true;
		}

		return false;
	}

	private static boolean probe(Minecraft client, Level level,
	                             Vec3 eyePos, Vec3 target, long packed) {
		if (client.player == null) return false;

		BlockHitResult hit = level.clip(new ClipContext(
				eyePos, target,
				ClipContext.Block.COLLIDER,
				ClipContext.Fluid.ANY,
				client.player
		));

		if (hit.getType() != HitResult.Type.BLOCK) {
			return true;
		}

		return hit.getBlockPos().asLong() == packed;
	}

	private static boolean isOnCooldown(long packed) {
		return failureCooldown.getOrDefault(packed, 0L) > tickCounter;
	}

	private static void cleanupCooldowns() {
		if (failureCooldown.isEmpty()) return;
		failureCooldown.long2LongEntrySet().removeIf(e -> e.getLongValue() <= tickCounter);
	}

	private static String blockName(Block block) {
		return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath();
	}

	public static void invalidateQueue() {
		pendingRaycasts.clear();
		pendingSet.clear();
		failureCooldown.clear();
	}
}
