package de.Exitus.glowblock;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GlowState {
	public static final class Cluster {
		public final LongOpenHashSet positions;
		public final int minX, minY, minZ;
		public final int maxX, maxY, maxZ;
		public final double centerX, centerY, centerZ;
		public final double beamX, beamY, beamZ;
		public final int count;

		Cluster(LongOpenHashSet positions, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
			this.positions = positions;
			this.minX = minX;
			this.minY = minY;
			this.minZ = minZ;
			this.maxX = maxX;
			this.maxY = maxY;
			this.maxZ = maxZ;
			this.centerX = (minX + maxX) / 2.0 + 0.5;
			this.centerY = (minY + maxY) / 2.0 + 0.5;
			this.centerZ = (minZ + maxZ) / 2.0 + 0.5;
			this.beamX = this.centerX;
			this.beamY = maxY + 1.0;
			this.beamZ = this.centerZ;
			this.count = positions.size();
		}
	}

	private static final Object2ObjectOpenHashMap<Block, GlowColorSpec> targetSpecs = new Object2ObjectOpenHashMap<>();
	private static final Map<Block, LongOpenHashSet> seenPositions = new Object2ObjectOpenHashMap<>();
	private static final Map<Block, List<Cluster>> clustersCache = new Object2ObjectOpenHashMap<>();
	private static final Set<Block> clustersDirty = new HashSet<>();
	private static Object currentLevelKey = null;

	private static final Map<String, GlowColorSpec> savedSpecs = new HashMap<>();

	private static boolean xrayEnabled = true;
	private static boolean beamsEnabled = true;
	private static boolean fillEnabled = true;

	private GlowState() {
	}

	public static void addTarget(Block block, GlowColorSpec spec) {
		if (block == null) return;
		if (spec == null) spec = GlowColorSpec.solid(GlowColors.DEFAULT);
		targetSpecs.put(block, spec);
		seenPositions.computeIfAbsent(block, unusedKey -> new LongOpenHashSet());
		saveSpecForBlock(block, spec);
	}

	public static void addTarget(Block block, int argbColor) {
		addTarget(block, GlowColorSpec.solid(argbColor));
	}

	public static void addTargetWithSavedColor(Block block) {
		GlowColorSpec spec = getSavedSpec(block);
		addTarget(block, spec.copy());
	}

	public static boolean removeTarget(Block block) {
		boolean had = targetSpecs.containsKey(block);
		targetSpecs.remove(block);
		seenPositions.remove(block);
		clustersCache.remove(block);
		clustersDirty.remove(block);
		return had;
	}

	public static boolean isTracked(Block block) {
		return targetSpecs.containsKey(block);
	}

	public static Set<Block> getTrackedBlocks() {
		return Collections.unmodifiableSet(targetSpecs.keySet());
	}

	public static GlowColorSpec getSpec(Block block) {
		GlowColorSpec spec = targetSpecs.get(block);
		return spec != null ? spec : GlowColorSpec.solid(GlowColors.DEFAULT);
	}

	public static boolean hasAnyTarget() {
		return !targetSpecs.isEmpty();
	}

	public static boolean isXrayEnabled() { return xrayEnabled; }
	public static void setXrayEnabled(boolean value) { xrayEnabled = value; }
	public static boolean isBeamsEnabled() { return beamsEnabled; }
	public static void setBeamsEnabled(boolean value) { beamsEnabled = value; }
	public static boolean isFillEnabled() { return fillEnabled; }
	public static void setFillEnabled(boolean value) { fillEnabled = value; }

	private static void saveSpecForBlock(Block block, GlowColorSpec spec) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(block);
		if (id != null) savedSpecs.put(id.toString(), spec.copy());
	}

	public static GlowColorSpec getSavedSpec(Block block) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(block);
		if (id != null) {
			GlowColorSpec saved = savedSpecs.get(id.toString());
			if (saved != null) return saved;
		}
		return GlowColorSpec.solid(GlowColors.forBlock(block));
	}

	public static void putSavedSpec(String blockId, GlowColorSpec spec) {
		savedSpecs.put(blockId, spec);
	}

	public static Map<String, GlowColorSpec> getAllSavedSpecs() {
		return Collections.unmodifiableMap(savedSpecs);
	}

	public static void markSeen(Block block, long packedPos) {
		LongOpenHashSet set = seenPositions.get(block);
		if (set != null && set.add(packedPos)) {
			clustersDirty.add(block);
		}
	}

	public static boolean isSeen(Block block, long packedPos) {
		LongOpenHashSet set = seenPositions.get(block);
		return set != null && set.contains(packedPos);
	}

	public static void removeSeen(Block block, long packedPos) {
		LongOpenHashSet set = seenPositions.get(block);
		if (set != null && set.remove(packedPos)) {
			clustersDirty.add(block);
		}
	}

	public static LongSet getSeenPositions(Block block) {
		LongOpenHashSet set = seenPositions.get(block);
		return set == null ? LongSets.EMPTY_SET : LongSets.unmodifiable(set);
	}

	public static int totalSeen() {
		int total = 0;
		for (LongOpenHashSet set : seenPositions.values()) total += set.size();
		return total;
	}

	public static List<Cluster> getClusters(Block block) {
		List<Cluster> cached = clustersCache.get(block);
		if (cached != null && !clustersDirty.contains(block)) {
			return cached;
		}
		LongOpenHashSet positions = seenPositions.get(block);
		if (positions == null || positions.isEmpty()) {
			clustersCache.put(block, Collections.emptyList());
			clustersDirty.remove(block);
			return Collections.emptyList();
		}
		List<Cluster> computed = computeClusters(positions);
		clustersCache.put(block, computed);
		clustersDirty.remove(block);
		return computed;
	}

	private static List<Cluster> computeClusters(LongOpenHashSet positions) {
		LongOpenHashSet remaining = new LongOpenHashSet(positions);
		List<Cluster> result = new ArrayList<>();
		LongArrayFIFOQueue queue = new LongArrayFIFOQueue();

		int[] ndx = {1, -1, 0, 0, 0, 0};
		int[] ndy = {0, 0, 1, -1, 0, 0};
		int[] ndz = {0, 0, 0, 0, 1, -1};

		while (!remaining.isEmpty()) {
			long start = remaining.iterator().nextLong();
			remaining.remove(start);
			queue.enqueue(start);

			LongOpenHashSet cluster = new LongOpenHashSet();
			cluster.add(start);

			int sx = BlockPos.getX(start);
			int sy = BlockPos.getY(start);
			int sz = BlockPos.getZ(start);
			int minX = sx, maxX = sx;
			int minY = sy, maxY = sy;
			int minZ = sz, maxZ = sz;

			while (!queue.isEmpty()) {
				long p = queue.dequeueLong();
				int x = BlockPos.getX(p);
				int y = BlockPos.getY(p);
				int z = BlockPos.getZ(p);

				for (int i = 0; i < 6; i++) {
					int nx = x + ndx[i];
					int ny = y + ndy[i];
					int nz = z + ndz[i];
					long np = BlockPos.asLong(nx, ny, nz);
					if (remaining.remove(np)) {
						cluster.add(np);
						queue.enqueue(np);
						if (nx < minX) minX = nx;
						if (nx > maxX) maxX = nx;
						if (ny < minY) minY = ny;
						if (ny > maxY) maxY = ny;
						if (nz < minZ) minZ = nz;
						if (nz > maxZ) maxZ = nz;
					}
				}
			}

			result.add(new Cluster(cluster, minX, minY, minZ, maxX, maxY, maxZ));
		}
		return result;
	}

	public static void checkWorldChange(Object levelKey) {
		if (currentLevelKey != null && !currentLevelKey.equals(levelKey)) {
			clearSeen();
		}
		currentLevelKey = levelKey;
	}

	public static void clearSeen() {
		for (Map.Entry<Block, LongOpenHashSet> entry : seenPositions.entrySet()) {
			entry.getValue().clear();
			clustersDirty.add(entry.getKey());
		}
	}

	public static void reset() {
		targetSpecs.clear();
		seenPositions.clear();
		clustersCache.clear();
		clustersDirty.clear();
	}
}
