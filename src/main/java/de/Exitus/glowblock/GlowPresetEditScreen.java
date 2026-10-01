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

public class GlowPresetEditScreen extends Screen {

	private static final int ROW_HEIGHT = 32;
	private static final int VISIBLE_ROWS = 6;

	private final Screen parent;
	private final GlowPreset preset;
	private List<String> blockIds;
	private int scrollOffset = 0;

	private int panelX, panelY, panelW, panelH;

	public GlowPresetEditScreen(Screen parent, GlowPreset preset) {
		super(Component.translatable("glowblock.gui.preset_edit.title", preset.getName()));
		this.parent = parent;
		this.preset = preset;
		this.blockIds = new ArrayList<>(preset.getBlocks().keySet());
	}

	@Override
	protected void init() {
		panelW = 420;
		panelH = 44 + VISIBLE_ROWS * ROW_HEIGHT + 46;
		panelX = (this.width - panelW) / 2;
		panelY = (this.height - panelH) / 2;
		buildWidgets();
	}

	private void refreshList() {
		blockIds = new ArrayList<>(preset.getBlocks().keySet());
	}

	private void buildWidgets() {
		clearWidgets();

		int listTop = panelY + 40;
		int maxScroll = Math.max(0, blockIds.size() - VISIBLE_ROWS);
		scrollOffset = Math.min(scrollOffset, maxScroll);

		for (int i = 0; i < VISIBLE_ROWS && (i + scrollOffset) < blockIds.size(); i++) {
			final String blockId = blockIds.get(i + scrollOffset);
			int rowY = listTop + i * ROW_HEIGHT + (ROW_HEIGHT - 20) / 2;

			addRenderableWidget(Button.builder(Component.translatable("glowblock.gui.button.color"), btn -> {
				Identifier id = Identifier.tryParse(blockId);
				if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
					Block block = BuiltInRegistries.BLOCK.getValue(id);
					GlowColorSpec current = preset.getSpec(blockId);
					this.minecraft.setScreenAndShow(new GlowColorScreen(this, block, current, newSpec -> {
						preset.putBlock(blockId, newSpec);
						preset.save();
					}));
				}
			}).bounds(panelX + panelW - 120, rowY, 70, 20).build());

			addRenderableWidget(Button.builder(Component.literal("\u00D7"), btn -> {
				preset.removeBlock(blockId);
				preset.save();
				refreshList();
				buildWidgets();
			}).bounds(panelX + panelW - 44, rowY, 20, 20).build());
		}

		int btnY = panelY + panelH - 36;

		addRenderableWidget(Button.builder(Component.translatable("glowblock.gui.button.add_block"), btn -> {
			this.minecraft.setScreenAndShow(new GlowAddBlockScreen(this, new GlowAddBlockScreen.BlockTarget() {
				@Override
				public boolean isTracked(Block block) {
					Identifier id = BuiltInRegistries.BLOCK.getKey(block);
					return id != null && preset.hasBlock(id.toString());
				}

				@Override
				public void toggle(Block block) {
					Identifier id = BuiltInRegistries.BLOCK.getKey(block);
					if (id == null) return;
					if (preset.hasBlock(id.toString())) {
						preset.removeBlock(id.toString());
					} else {
						preset.putBlock(id.toString(), GlowColorSpec.solid(GlowColors.forBlock(block)));
					}
					preset.save();
				}
			}));
		}).bounds(panelX + 16, btnY, 80, 20).build());

		addRenderableWidget(Button.builder(Component.translatable("glowblock.gui.preset_edit.save_back"), btn -> {
			preset.save();
			this.minecraft.setScreenAndShow(parent);
		}).bounds(panelX + panelW / 2 - 65, btnY, 130, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
		int listTop = panelY + 40;

		gfx.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xE0181818);
		gfx.outline(panelX, panelY, panelW, panelH, 0xFF444444);

		String titleText = Component.translatable("glowblock.gui.preset_edit.title", preset.getName()).getString();
		gfx.text(this.font, titleText, panelX + 16, panelY + 12, 0xFFFFFFFF);
		String countText = Component.translatable("glowblock.gui.preset_list.blocks", blockIds.size()).getString();
		int cw = this.font.width(countText);
		gfx.text(this.font, countText, panelX + panelW - 16 - cw, panelY + 12, 0xFFAAAAAA);

		gfx.fill(panelX + 8, panelY + 30, panelX + panelW - 8, panelY + 31, 0xFF333333);

		if (blockIds.isEmpty()) {
			gfx.centeredText(this.font, Component.translatable("glowblock.gui.preset_edit.empty_title"),
					this.width / 2, listTop + 40, 0xFF888888);
			gfx.centeredText(this.font, Component.translatable("glowblock.gui.preset_edit.empty_hint"),
					this.width / 2, listTop + 56, 0xFF666666);
		} else {
			for (int i = 0; i < VISIBLE_ROWS && (i + scrollOffset) < blockIds.size(); i++) {
				String blockId = blockIds.get(i + scrollOffset);
				int rowY = listTop + i * ROW_HEIGHT;

				int rowBg = (i % 2 == 0) ? 0x20FFFFFF : 0x10FFFFFF;
				gfx.fill(panelX + 8, rowY, panelX + panelW - 8, rowY + ROW_HEIGHT - 2, rowBg);

				GlowColorSpec spec = preset.getSpec(blockId);
				int sqX = panelX + 44;
				int sqY = rowY + (ROW_HEIGHT - 16) / 2;
				GlowConfigScreen.drawSpecSwatch(gfx, sqX, sqY, 18, 18, spec);

				Identifier id = Identifier.tryParse(blockId);
				String name = id != null ? id.getPath() : blockId;
				gfx.text(this.font, name, panelX + 72, rowY + (ROW_HEIGHT - 9) / 2, 0xFFDDDDDD);

				String modeTag = GlowConfigScreen.modeTag(spec);
				if (!modeTag.isEmpty()) {
					int tw = this.font.width(modeTag);
					gfx.text(this.font, modeTag,
							panelX + panelW - 132 - tw, rowY + (ROW_HEIGHT - 9) / 2, 0xFF888888);
				}
			}
		}

		gfx.fill(panelX + 8, panelY + panelH - 44, panelX + panelW - 8, panelY + panelH - 43, 0xFF333333);

		super.extractRenderState(gfx, mouseX, mouseY, delta);

		if (!blockIds.isEmpty()) {
			for (int i = 0; i < VISIBLE_ROWS && (i + scrollOffset) < blockIds.size(); i++) {
				String blockId = blockIds.get(i + scrollOffset);
				int rowY = listTop + i * ROW_HEIGHT;
				Identifier id = Identifier.tryParse(blockId);
				if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
					Block block = BuiltInRegistries.BLOCK.getValue(id);
					try {
						gfx.item(new ItemStack(block), panelX + 18, rowY + (ROW_HEIGHT - 18) / 2);
					} catch (Exception ignored) {}
				}
			}
		}
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		int maxScroll = Math.max(0, blockIds.size() - VISIBLE_ROWS);
		scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - scrollY));
		buildWidgets();
		return true;
	}

	@Override
	public void onClose() { preset.save(); this.minecraft.setScreenAndShow(parent); }

	@Override
	public boolean isPauseScreen() { return false; }
}
