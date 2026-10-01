package de.Exitus.glowblock;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

public class GlowConfigScreen extends Screen {

	private static final int ROW_HEIGHT = 36;
	private static final int VISIBLE_ROWS = 6;

	private int scrollOffset = 0;
	private List<Block> trackedList = new ArrayList<>();

	private int panelX, panelY, panelW, panelH;

	public GlowConfigScreen() {
		super(Component.translatable("glowblock.gui.title"));
	}

	@Override
	protected void init() {
		refreshTrackedList();
		panelW = 420;
		panelH = 44 + VISIBLE_ROWS * ROW_HEIGHT + 40;
		panelX = (this.width - panelW) / 2;
		panelY = (this.height - panelH) / 2;
		buildWidgets();
	}

	private void refreshTrackedList() {
		trackedList = new ArrayList<>(GlowState.getTrackedBlocks());
	}

	private void buildWidgets() {
		clearWidgets();

		int listTop = panelY + 40;
		int maxScroll = Math.max(0, trackedList.size() - VISIBLE_ROWS);
		scrollOffset = Math.min(scrollOffset, maxScroll);

		for (int i = 0; i < VISIBLE_ROWS && (i + scrollOffset) < trackedList.size(); i++) {
			final Block block = trackedList.get(i + scrollOffset);
			int rowY = listTop + i * ROW_HEIGHT + (ROW_HEIGHT - 20) / 2;

			addRenderableWidget(Button.builder(Component.translatable("glowblock.gui.button.color"), btn -> {
				GlowColorSpec current = GlowState.getSpec(block);
				this.minecraft.setScreenAndShow(new GlowColorScreen(this, block, current, newSpec -> {
					GlowState.addTarget(block, newSpec);
					GlowConfig.markDirty();
				}));
			}).bounds(panelX + panelW - 120, rowY, 70, 20).build());

			addRenderableWidget(Button.builder(Component.literal("\u00D7"), btn -> {
				GlowState.removeTarget(block);
				GlowVision.invalidateQueue();
				GlowConfig.markDirty();
				refreshTrackedList();
				buildWidgets();
			}).bounds(panelX + panelW - 44, rowY, 20, 20).build());
		}

		int btnY = panelY + panelH - 32;
		addRenderableWidget(Button.builder(Component.translatable("glowblock.gui.button.clear_all"), btn -> {
			GlowState.reset();
			GlowVision.invalidateQueue();
			GlowConfig.markDirty();
			refreshTrackedList();
			buildWidgets();
		}).bounds(panelX + 16, btnY, 90, 20).build());

		addRenderableWidget(Button.builder(Component.translatable("glowblock.gui.tab.presets"), btn -> {
			this.minecraft.setScreenAndShow(new GlowPresetListScreen(this));
		}).bounds(panelX + 112, btnY, 60, 20).build());

		addRenderableWidget(Button.builder(Component.translatable("glowblock.gui.button.add_block"), btn -> {
			this.minecraft.setScreenAndShow(new GlowAddBlockScreen(this));
		}).bounds(panelX + panelW / 2 + 10, btnY, 70, 20).build());

		addRenderableWidget(Button.builder(Component.translatable("glowblock.gui.button.done"), btn -> {
			this.minecraft.setScreenAndShow(null);
		}).bounds(panelX + panelW - 96, btnY, 80, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
		int listTop = panelY + 40;

		gfx.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xE0181818);
		gfx.outline(panelX, panelY, panelW, panelH, 0xFF444444);

		String titleText = Component.translatable("glowblock.gui.title").getString();
		gfx.text(this.font, titleText, panelX + 16, panelY + 12, 0xFFFFFFFF);

		String countText = Component.translatable("glowblock.gui.config.count", trackedList.size()).getString();
		int countW = this.font.width(countText);
		gfx.text(this.font, countText, panelX + panelW - 16 - countW, panelY + 12, 0xFFAAAAAA);

		gfx.fill(panelX + 8, panelY + 30, panelX + panelW - 8, panelY + 31, 0xFF333333);

		if (trackedList.isEmpty()) {
			int cy = panelY + panelH / 2 - 16;
			gfx.centeredText(this.font, Component.translatable("glowblock.gui.config.empty_title"),
					this.width / 2, cy, 0xFF888888);
			gfx.centeredText(this.font, Component.translatable("glowblock.gui.config.empty_hint"),
					this.width / 2, cy + 16, 0xFF666666);
		} else {
			for (int i = 0; i < VISIBLE_ROWS && (i + scrollOffset) < trackedList.size(); i++) {
				Block block = trackedList.get(i + scrollOffset);
				int rowY = listTop + i * ROW_HEIGHT;

				int rowBg = (i % 2 == 0) ? 0x20FFFFFF : 0x10FFFFFF;
				gfx.fill(panelX + 8, rowY, panelX + panelW - 8, rowY + ROW_HEIGHT - 2, rowBg);

				GlowColorSpec spec = GlowState.getSpec(block);
				int sqX = panelX + 44;
				int sqY = rowY + (ROW_HEIGHT - 16) / 2;
				drawSpecSwatch(gfx, sqX, sqY, 18, 18, spec);

				String name;
				try {
					name = block.getName().getString();
				} catch (Exception e) {
					Identifier id = BuiltInRegistries.BLOCK.getKey(block);
					name = id != null ? id.getPath() : "?";
				}
				gfx.text(this.font, name, panelX + 72, rowY + (ROW_HEIGHT - 9) / 2, 0xFFDDDDDD);

				String modeTag = modeTag(spec);
				if (!modeTag.isEmpty()) {
					int tw = this.font.width(modeTag);
					gfx.text(this.font, modeTag,
							panelX + panelW - 132 - tw, rowY + (ROW_HEIGHT - 9) / 2, 0xFF888888);
				}
			}
		}

		int sepY = panelY + panelH - 42;
		gfx.fill(panelX + 8, sepY, panelX + panelW - 8, sepY + 1, 0xFF333333);

		super.extractRenderState(gfx, mouseX, mouseY, delta);

		if (!trackedList.isEmpty()) {
			for (int i = 0; i < VISIBLE_ROWS && (i + scrollOffset) < trackedList.size(); i++) {
				Block block = trackedList.get(i + scrollOffset);
				int rowY = listTop + i * ROW_HEIGHT;
				int iconX = panelX + 18;
				int iconY = rowY + (ROW_HEIGHT - 18) / 2;
				try {
					ItemStack stack = new ItemStack(block);
					gfx.item(stack, iconX, iconY);
				} catch (Exception ignored) {}
			}
		}
	}

	static void drawSpecSwatch(GuiGraphicsExtractor gfx, int x, int y, int w, int h, GlowColorSpec spec) {
		if (spec.isRainbow()) {
			float tShift = (System.currentTimeMillis() % 4000L) / 4000.0f;
			for (int i = 0; i < w; i++) {
				float hue = ((float) i / w + tShift) % 1f;
				gfx.fill(x + i, y, x + i + 1, y + h, 0xFF000000 | hsvToRgb(hue));
			}
		} else {
			gfx.fill(x, y, x + w, y + h, spec.getSolidColor());
		}
		gfx.outline(x, y, w, h, 0xFF333333);
	}

	static String modeTag(GlowColorSpec spec) {
		return spec.isRainbow() ? Component.translatable("glowblock.gui.config.rainbow").getString() : "";
	}

	private static int hsvToRgb(float h) {
		float s = 1.0f, v = 1.0f;
		float r = 0f, g = 0f, b = 0f;
		int i = (int) Math.floor(h * 6f);
		float f = h * 6f - i;
		float p = v * (1f - s);
		float q = v * (1f - f * s);
		float t = v * (1f - (1f - f) * s);
		switch (Math.floorMod(i, 6)) {
			case 0 -> { r = v; g = t; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			case 5 -> { r = v; g = p; b = q; }
		}
		return (Math.round(r * 255f) << 16) | (Math.round(g * 255f) << 8) | Math.round(b * 255f);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		int maxScroll = Math.max(0, trackedList.size() - VISIBLE_ROWS);
		scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - scrollY));
		buildWidgets();
		return true;
	}

	@Override
	public boolean isPauseScreen() { return false; }
}
