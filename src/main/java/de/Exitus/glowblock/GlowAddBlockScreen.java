package de.Exitus.glowblock;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

public class GlowAddBlockScreen extends Screen {

	public interface BlockTarget {
		boolean isTracked(Block block);
		void toggle(Block block);
	}

	private static final BlockTarget LIVE_TARGET = new BlockTarget() {
		@Override
		public boolean isTracked(Block block) {
			return GlowState.isTracked(block);
		}

		@Override
		public void toggle(Block block) {
			if (GlowState.isTracked(block)) {
				GlowState.removeTarget(block);
			} else {
				GlowState.addTargetWithSavedColor(block);
			}
			GlowVision.invalidateQueue();
			GlowConfig.markDirty();
		}
	};

	private static final int CELL = 40;
	private static final int COLS = 10;

	private final Screen parent;
	private final BlockTarget target;
	private EditBox searchField;
	private List<Block> filtered = new ArrayList<>();
	private int scrollOffset = 0;
	private int visibleRows;

	private int panelX, panelY, panelW, panelH;
	private int gridTop;

	public GlowAddBlockScreen(Screen parent) {
		this(parent, LIVE_TARGET);
	}

	public GlowAddBlockScreen(Screen parent, BlockTarget target) {
		super(Component.translatable("glowblock.gui.add_block.title"));
		this.parent = parent;
		this.target = target;
	}

	@Override
	protected void init() {
		panelW = COLS * CELL + 32;
		panelH = this.height - 40;
		panelX = (this.width - panelW) / 2;
		panelY = 20;

		gridTop = panelY + 62;
		visibleRows = (panelY + panelH - 40 - gridTop) / CELL;

		searchField = new EditBox(this.font, panelX + 16, panelY + 36, panelW - 32, 20,
				Component.translatable("glowblock.gui.add_block.search_label"));
		searchField.setHint(Component.translatable("glowblock.gui.add_block.search_hint"));
		searchField.setMaxLength(100);
		searchField.setResponder(s -> { scrollOffset = 0; updateFilter(); });
		addRenderableWidget(searchField);

		addRenderableWidget(Button.builder(Component.translatable("glowblock.gui.button.back"), btn -> {
			this.minecraft.setScreenAndShow(parent);
		}).bounds(panelX + panelW - 96, panelY + panelH - 28, 80, 20).build());

		updateFilter();
	}

	private void updateFilter() {
		filtered.clear();
		String raw = searchField != null ? searchField.getValue().trim().toLowerCase() : "";
		String query = raw.replace(' ', '_');

		for (Block block : BuiltInRegistries.BLOCK) {
			if (block == Blocks.AIR) continue;
			ItemStack testStack = new ItemStack(block);
			if (testStack.isEmpty()) continue;

			Identifier id = BuiltInRegistries.BLOCK.getKey(block);
			if (id == null) continue;

			String path = id.getPath();
			String pretty = path.replace('_', ' ');

			String localized = "";
			try {
				localized = block.getName().getString().toLowerCase();
			} catch (Exception ignored) {}

			if (query.isEmpty()
					|| path.contains(query)
					|| id.toString().contains(query)
					|| pretty.contains(raw)
					|| (!localized.isEmpty() && localized.contains(raw))) {
				filtered.add(block);
			}
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {

		gfx.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xE0181818);
		gfx.outline(panelX, panelY, panelW, panelH, 0xFF444444);

		String titleText = Component.translatable("glowblock.gui.add_block.title").getString();
		gfx.text(this.font, titleText, panelX + 16, panelY + 12, 0xFFFFFFFF);

		String countText = Component.translatable("glowblock.gui.add_block.count", filtered.size()).getString();
		int countW = this.font.width(countText);
		gfx.text(this.font, countText, panelX + panelW - 16 - countW, panelY + 12, 0xFFAAAAAA);

		gfx.fill(panelX + 8, panelY + 28, panelX + panelW - 8, panelY + 29, 0xFF333333);

		int gridLeft = panelX + 16;
		int totalRows = (filtered.size() + COLS - 1) / COLS;
		int maxScroll = Math.max(0, totalRows - visibleRows);
		scrollOffset = Math.min(scrollOffset, maxScroll);

		for (int row = 0; row < visibleRows; row++) {
			int dataRow = row + scrollOffset;
			for (int col = 0; col < COLS; col++) {
				int idx = dataRow * COLS + col;
				if (idx >= filtered.size()) break;

				Block block = filtered.get(idx);
				int cellX = gridLeft + col * CELL;
				int cellY = gridTop + row * CELL;

				boolean hovered = mouseX >= cellX && mouseX < cellX + CELL - 2
						&& mouseY >= cellY && mouseY < cellY + CELL - 2;
				boolean tracked = target.isTracked(block);

				int cellBg = 0xFF2A2A2A;
				if (hovered) cellBg = 0xFF3A3A4A;
				gfx.fill(cellX, cellY, cellX + CELL - 2, cellY + CELL - 2, cellBg);

				if (tracked) {
					gfx.outline(cellX, cellY, CELL - 2, CELL - 2, 0xFFDD44DD);
				} else if (hovered) {
					gfx.outline(cellX, cellY, CELL - 2, CELL - 2, 0xFFAAAAFF);
				}

				int iconX = cellX + (CELL - 18) / 2;
				int iconY = cellY + (CELL - 18) / 2;
				try {
					gfx.item(new ItemStack(block), iconX, iconY);
				} catch (Exception ignored) {}
			}
		}

		super.extractRenderState(gfx, mouseX, mouseY, delta);

		for (int row = 0; row < visibleRows; row++) {
			int dataRow = row + scrollOffset;
			for (int col = 0; col < COLS; col++) {
				int idx = dataRow * COLS + col;
				if (idx >= filtered.size()) break;

				int cellX = gridLeft + col * CELL;
				int cellY = gridTop + row * CELL;
				if (mouseX >= cellX && mouseX < cellX + CELL - 2
						&& mouseY >= cellY && mouseY < cellY + CELL - 2) {
					Block block = filtered.get(idx);
					String display;
					try {
						display = block.getName().getString();
					} catch (Exception e) {
						display = "";
					}
					if (display == null || display.isEmpty()) {
						Identifier id = BuiltInRegistries.BLOCK.getKey(block);
						String name = id != null ? id.getPath().replace('_', ' ') : "unknown";
						StringBuilder sb = new StringBuilder();
						for (String word : name.split(" ")) {
							if (!sb.isEmpty()) sb.append(' ');
							if (!word.isEmpty()) sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
						}
						display = sb.toString();
					}
					int tw = this.font.width(display);
					int tx = mouseX + 12;
					int ty = mouseY - 4;
					if (tx + tw + 6 > this.width) tx = mouseX - tw - 12;
					gfx.fill(tx - 3, ty - 2, tx + tw + 3, ty + 11, 0xEE1A1A2E);
					gfx.outline(tx - 3, ty - 2, tw + 6, 13, 0xFF6040B0);
					gfx.text(this.font, display, tx, ty, 0xFFFFFFFF);
				}
			}
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.buttonInfo().button() == 0) {
			int gridLeft = panelX + 16;
			for (int row = 0; row < visibleRows; row++) {
				int dataRow = row + scrollOffset;
				for (int col = 0; col < COLS; col++) {
					int idx = dataRow * COLS + col;
					if (idx >= filtered.size()) break;

					int cellX = gridLeft + col * CELL;
					int cellY = gridTop + row * CELL;
					if (event.x() >= cellX && event.x() < cellX + CELL - 2
							&& event.y() >= cellY && event.y() < cellY + CELL - 2) {
						target.toggle(filtered.get(idx));
						return true;
					}
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		int totalRows = (filtered.size() + COLS - 1) / COLS;
		int maxScroll = Math.max(0, totalRows - visibleRows);
		scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - scrollY));
		return true;
	}

	@Override
	public void onClose() { this.minecraft.setScreenAndShow(parent); }

	@Override
	public boolean isPauseScreen() { return false; }
}
