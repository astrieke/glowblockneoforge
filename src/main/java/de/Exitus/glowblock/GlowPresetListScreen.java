package de.Exitus.glowblock;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GlowPresetListScreen extends Screen {

	private static final int ROW_HEIGHT = 28;
	private static final int VISIBLE_ROWS = 7;

	private static final int TITLE_H = 30;
	private static final int TAB_H = 24;
	private static final int LIST_TOP_OFFSET = TITLE_H + TAB_H + 6;
	private static final int FOOTER_H = 46;

	private static final int TAB_PRESETS = 0;
	private static final int TAB_CONFIG = 1;

	private final Screen parent;
	private List<String> presetNames;
	private Map<String, Integer> presetBlockCounts = new HashMap<>();
	private int scrollOffset = 0;
	private int currentTab = TAB_PRESETS;

	private EditBox nameField;
	private boolean showNameField = false;
	private String renamingPreset = null;

	private int panelX, panelY, panelW, panelH;

	public GlowPresetListScreen(Screen parent) {
		super(Component.translatable("glowblock.gui.tab.presets"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		presetNames = GlowPreset.listAll();
		cacheBlockCounts();
		panelW = 520;
		panelH = LIST_TOP_OFFSET + VISIBLE_ROWS * ROW_HEIGHT + FOOTER_H;
		panelX = (this.width - panelW) / 2;
		panelY = (this.height - panelH) / 2;
		buildWidgets();
	}

	private void cacheBlockCounts() {
		presetBlockCounts.clear();
		for (String name : presetNames) {
			GlowPreset p = GlowPreset.load(name);
			presetBlockCounts.put(name, p.getBlocks().size());
		}
	}

	private void buildWidgets() {
		clearWidgets();

		if (showNameField) {
			buildNameField();
			return;
		}

		buildTabBar();

		if (currentTab == TAB_PRESETS) {
			buildPresetsTab();
		} else {
			buildConfigTab();
		}
	}

	private void buildTabBar() {
		int tabY = panelY + TITLE_H + 4;

		Button presetsTab = Button.builder(Component.translatable("glowblock.gui.tab.presets"), btn -> {
			if (currentTab != TAB_PRESETS) {
				currentTab = TAB_PRESETS;
				buildWidgets();
			}
		}).bounds(panelX + 16, tabY, 80, 20).build();
		presetsTab.active = (currentTab != TAB_PRESETS);
		addRenderableWidget(presetsTab);

		Button configTab = Button.builder(Component.translatable("glowblock.gui.tab.config"), btn -> {
			if (currentTab != TAB_CONFIG) {
				currentTab = TAB_CONFIG;
				buildWidgets();
			}
		}).bounds(panelX + 100, tabY, 80, 20).build();
		configTab.active = (currentTab != TAB_CONFIG);
		addRenderableWidget(configTab);
	}

	private void buildPresetsTab() {
		int listTop = panelY + LIST_TOP_OFFSET;
		int maxScroll = Math.max(0, presetNames.size() - VISIBLE_ROWS);
		scrollOffset = Math.min(scrollOffset, maxScroll);

		for (int i = 0; i < VISIBLE_ROWS && (i + scrollOffset) < presetNames.size(); i++) {
			final String name = presetNames.get(i + scrollOffset);
			int rowY = listTop + i * ROW_HEIGHT + (ROW_HEIGHT - 20) / 2;

			addRenderableWidget(Button.builder(Component.translatable("glowblock.gui.preset_list.load"), btn -> {
				loadPreset(name);
				this.minecraft.setScreenAndShow(parent);
			}).bounds(panelX + panelW - 240, rowY, 50, 20).build());

			addRenderableWidget(Button.builder(Component.translatable("glowblock.gui.preset_list.edit"), btn -> {
				GlowPreset preset = GlowPreset.load(name);
				this.minecraft.setScreenAndShow(new GlowPresetEditScreen(this, preset));
			}).bounds(panelX + panelW - 184, rowY, 40, 20).build());

			addRenderableWidget(Button.builder(Component.translatable("glowblock.gui.preset_list.rename"), btn -> {
				showNameField = true;
				renamingPreset = name;
				buildWidgets();
			}).bounds(panelX + panelW - 138, rowY, 80, 20).build());

			addRenderableWidget(Button.builder(Component.literal("\u00D7"), btn -> {
				GlowPreset preset = GlowPreset.load(name);
				preset.deleteFile();
				presetNames = GlowPreset.listAll();
				cacheBlockCounts();
				buildWidgets();
			}).bounds(panelX + panelW - 52, rowY, 20, 20).build());
		}

		int btnY = panelY + panelH - 36;
		addRenderableWidget(Button.builder(Component.translatable("glowblock.gui.preset_list.new"), btn -> {
			showNameField = true;
			renamingPreset = null;
			buildWidgets();
		}).bounds(panelX + 16, btnY, 100, 20).build());

		addRenderableWidget(Button.builder(Component.translatable("glowblock.gui.preset_list.save_current"), btn -> {
			showNameField = true;
			renamingPreset = null;
			buildWidgets();
		}).bounds(panelX + panelW / 2 - 60, btnY, 120, 20).build());

		addRenderableWidget(Button.builder(Component.translatable("glowblock.gui.button.back"), btn -> {
			this.minecraft.setScreenAndShow(parent);
		}).bounds(panelX + panelW - 96, btnY, 80, 20).build());
	}

	private void buildConfigTab() {
		int contentTop = panelY + LIST_TOP_OFFSET + 8;
		int rowSpacing = 28;
		int rowW = panelW - 32;

		boolean xray = GlowState.isXrayEnabled();
		addRenderableWidget(Button.builder(
				Component.translatable("glowblock.gui.preset_list.view", onOff(xray)),
				btn -> {
					GlowState.setXrayEnabled(!GlowState.isXrayEnabled());
					GlowConfig.markDirty();
					buildWidgets();
				}
		).bounds(panelX + 16, contentTop, rowW, 20).build());

		boolean beams = GlowState.isBeamsEnabled();
		addRenderableWidget(Button.builder(
				Component.translatable("glowblock.gui.preset_list.beam", onOff(beams)),
				btn -> {
					GlowState.setBeamsEnabled(!GlowState.isBeamsEnabled());
					GlowConfig.markDirty();
					buildWidgets();
				}
		).bounds(panelX + 16, contentTop + rowSpacing, rowW, 20).build());

		boolean fill = GlowState.isFillEnabled();
		addRenderableWidget(Button.builder(
				Component.translatable("glowblock.gui.preset_list.fill", onOff(fill)),
				btn -> {
					GlowState.setFillEnabled(!GlowState.isFillEnabled());
					GlowConfig.markDirty();
					buildWidgets();
				}
		).bounds(panelX + 16, contentTop + rowSpacing * 2, rowW, 20).build());

		int btnY = panelY + panelH - 36;
		addRenderableWidget(Button.builder(Component.translatable("glowblock.gui.button.back"), btn -> {
			this.minecraft.setScreenAndShow(parent);
		}).bounds(panelX + panelW - 96, btnY, 80, 20).build());
	}

	private static Component onOff(boolean on) {
		return Component.translatable(on ? "glowblock.gui.on" : "glowblock.gui.off");
	}

	private void buildNameField() {
		int fieldY = panelY + panelH / 2 - 20;
		nameField = new EditBox(this.font, panelX + 30, fieldY, panelW - 60, 20,
				Component.translatable("glowblock.gui.preset_list.name_hint"));
		nameField.setMaxLength(40);
		if (renamingPreset != null) {
			nameField.setValue(renamingPreset);
		}
		addRenderableWidget(nameField);

		int btnY2 = fieldY + 30;
		addRenderableWidget(Button.builder(Component.translatable("glowblock.gui.button.ok"), btn -> {
			String newName = nameField.getValue().trim();
			if (!newName.isEmpty()) {
				if (renamingPreset != null) {
					GlowPreset preset = GlowPreset.load(renamingPreset);
					preset.rename(newName);
					preset.save();
				} else {
					GlowPreset preset = GlowPreset.fromCurrentState(newName);
					preset.save();
				}
				presetNames = GlowPreset.listAll();
				cacheBlockCounts();
			}
			showNameField = false;
			renamingPreset = null;
			buildWidgets();
		}).bounds(panelX + panelW / 2 - 80, btnY2, 70, 20).build());

		addRenderableWidget(Button.builder(Component.translatable("glowblock.gui.button.cancel"), btn -> {
			showNameField = false;
			renamingPreset = null;
			buildWidgets();
		}).bounds(panelX + panelW / 2 + 10, btnY2, 70, 20).build());
	}

	private void loadPreset(String name) {
		GlowPreset preset = GlowPreset.load(name);

		GlowState.reset();

		for (var entry : preset.getBlocks().entrySet()) {
			Identifier id = Identifier.tryParse(entry.getKey());
			if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
				Block block = BuiltInRegistries.BLOCK.getValue(id);
				GlowState.addTarget(block, entry.getValue());
			}
		}
		GlowVision.invalidateQueue();
		GlowConfig.markDirty();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {

		gfx.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xE0181818);
		gfx.outline(panelX, panelY, panelW, panelH, 0xFF444444);

		String titleText = Component.translatable("glowblock.gui.title").getString();
		gfx.text(this.font, titleText, panelX + 16, panelY + 12, 0xFFFFFFFF);

		if (!showNameField && currentTab == TAB_PRESETS) {
			String countText = Component.translatable("glowblock.gui.preset_list.count", presetNames.size()).getString();
			int cw = this.font.width(countText);
			gfx.text(this.font, countText, panelX + panelW - 16 - cw, panelY + 12, 0xFFAAAAAA);
		}

		gfx.fill(panelX + 8, panelY + TITLE_H, panelX + panelW - 8, panelY + TITLE_H + 1, 0xFF333333);

		if (!showNameField) {
			gfx.fill(panelX + 8, panelY + TITLE_H + TAB_H + 4, panelX + panelW - 8, panelY + TITLE_H + TAB_H + 5, 0xFF333333);

			if (currentTab == TAB_PRESETS) {
				renderPresetsTab(gfx);
			}

			gfx.fill(panelX + 8, panelY + panelH - 44, panelX + panelW - 8, panelY + panelH - 43, 0xFF333333);
		} else {
			Component label = Component.translatable(renamingPreset != null
					? "glowblock.gui.preset_list.name_rename_label"
					: "glowblock.gui.preset_list.name_new_label");
			gfx.centeredText(this.font, label,
					this.width / 2, panelY + panelH / 2 - 36, 0xFFCCCCCC);
		}

		super.extractRenderState(gfx, mouseX, mouseY, delta);
	}

	private void renderPresetsTab(GuiGraphicsExtractor gfx) {
		int listTop = panelY + LIST_TOP_OFFSET;
		if (presetNames.isEmpty()) {
			gfx.centeredText(this.font, Component.translatable("glowblock.gui.preset_list.empty_title"),
					this.width / 2, listTop + 40, 0xFF888888);
			gfx.centeredText(this.font, Component.translatable("glowblock.gui.preset_list.empty_hint"),
					this.width / 2, listTop + 56, 0xFF666666);
			return;
		}

		for (int i = 0; i < VISIBLE_ROWS && (i + scrollOffset) < presetNames.size(); i++) {
			String name = presetNames.get(i + scrollOffset);
			int rowY = listTop + i * ROW_HEIGHT;

			int rowBg = (i % 2 == 0) ? 0x20FFFFFF : 0x10FFFFFF;
			gfx.fill(panelX + 8, rowY, panelX + panelW - 8, rowY + ROW_HEIGHT - 2, rowBg);

			gfx.text(this.font, name, panelX + 18, rowY + (ROW_HEIGHT - 9) / 2, 0xFFDDDDDD);

			int blockCount = presetBlockCounts.getOrDefault(name, 0);
			String info = Component.translatable("glowblock.gui.preset_list.blocks", blockCount).getString();
			int iw = this.font.width(info);
			gfx.text(this.font, info, panelX + panelW - 250 - iw, rowY + (ROW_HEIGHT - 9) / 2, 0xFF888888);
		}
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		if (!showNameField && currentTab == TAB_PRESETS) {
			int maxScroll = Math.max(0, presetNames.size() - VISIBLE_ROWS);
			scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - scrollY));
			buildWidgets();
		}
		return true;
	}

	@Override
	public void onClose() { this.minecraft.setScreenAndShow(parent); }

	@Override
	public boolean isPauseScreen() { return false; }
}
