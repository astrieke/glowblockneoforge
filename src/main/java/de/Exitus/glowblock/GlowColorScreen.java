package de.Exitus.glowblock;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

public class GlowColorScreen extends Screen {
	private static final int[] PRESETS = {
			0xFFFF3333, 0xFFFF8800, 0xFFFFCC00, 0xFF33FF33,
			0xFF00FFCC, 0xFF3388FF, 0xFF8833FF, 0xFFFF0077,
	};

	private final Screen parent;
	private final Block block;
	private final Consumer<GlowColorSpec> onSpecChanged;

	private final GlowColorSpec spec;

	private EditBox hexField;
	private int draggingSlider = -1;

	private int panelX, panelY, panelW, panelH;
	private int sliderX, sliderW, sliderY0;
	private static final int SLIDER_H = 20;
	private static final int SLIDER_GAP = 6;
	private static final int HANDLE_W = 6;

	private int hexRowY;
	private int presetY;

	public GlowColorScreen(Screen parent, Block block, GlowColorSpec currentSpec, Consumer<GlowColorSpec> onSpecChanged) {
		super(Component.translatable("glowblock.gui.color.title"));
		this.parent = parent;
		this.block = block;
		this.onSpecChanged = onSpecChanged;
		this.spec = currentSpec != null ? currentSpec.copy() : GlowColorSpec.solid(GlowColors.DEFAULT);
	}

	@Override
	protected void init() {
		panelW = 320;
		panelH = 250;
		panelX = (this.width - panelW) / 2;
		panelY = (this.height - panelH) / 2;

		int innerLeft = panelX + 16;
		int innerRight = panelX + panelW - 16;
		sliderW = innerRight - innerLeft;
		sliderX = innerLeft;

		hexRowY = panelY + 44;
		sliderY0 = hexRowY + 30;
		presetY = sliderY0 + 3 * (SLIDER_H + SLIDER_GAP) + 16;

		buildWidgets();
	}

	private void rebuild() {
		clearWidgets();
		buildWidgets();
	}

	private void buildWidgets() {
		int cx = this.width / 2;
		int innerLeft = panelX + 16;

		hexField = new EditBox(this.font, innerLeft, hexRowY, sliderW - 70, 20,
				Component.translatable("glowblock.gui.color.hex"));
		hexField.setMaxLength(7);
		if (spec.isRainbow()) {
			hexField.setValue("#RAINBOW");
			hexField.setEditable(false);
		} else {
			hexField.setValue(toHex(spec.getSolidColor()));
			hexField.setResponder(this::onHexChanged);
		}
		addRenderableWidget(hexField);

		int presetCount = PRESETS.length + 1;
		int presetSize = 20;
		int presetGap = 4;
		int totalPresetsW = presetCount * presetSize + (presetCount - 1) * presetGap;
		int presetStartX = cx - totalPresetsW / 2;

		for (int i = 0; i < PRESETS.length; i++) {
			final int color = PRESETS[i];
			int px = presetStartX + i * (presetSize + presetGap);
			addRenderableWidget(Button.builder(Component.literal(" "), btn -> {
				spec.setSolidColor(color);
				pushChange();
				rebuild();
			}).bounds(px, presetY, presetSize, presetSize).build());
		}

		int rainbowPx = presetStartX + PRESETS.length * (presetSize + presetGap);
		addRenderableWidget(Button.builder(Component.literal(" "), btn -> {
			spec.setRainbow();
			pushChange();
			rebuild();
		}).bounds(rainbowPx, presetY, presetSize, presetSize).build());

		addRenderableWidget(Button.builder(Component.translatable("glowblock.gui.button.done"), btn -> {
			pushChange();
			this.minecraft.setScreenAndShow(parent);
		}).bounds(cx - 50, panelY + panelH - 28, 100, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
		int cx = this.width / 2;

		gfx.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xCC101010);
		gfx.outline(panelX, panelY, panelW, panelH, 0xFF555555);
		gfx.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + 2, 0xFF6040B0);

		gfx.centeredText(this.font, this.title, cx, panelY + 8, 0xFFFFFFFF);

		String blockName;
		try {
			blockName = block.getName().getString();
		} catch (Exception e) {
			blockName = String.valueOf(BuiltInRegistries.BLOCK.getKey(block));
		}
		gfx.centeredText(this.font, Component.literal(blockName), cx, panelY + 22, 0xFF999999);

		gfx.fill(panelX + 8, panelY + 36, panelX + panelW - 8, panelY + 37, 0xFF333333);

		int previewX = panelX + panelW - 16 - 60;
		int previewY = hexRowY;
		int previewColor = spec.isRainbow()
				? spec.previewColor()
				: spec.getSolidColor();
		gfx.fill(previewX, previewY, previewX + 60, previewY + 20, previewColor);
		gfx.outline(previewX, previewY, 60, 20, 0xFF444444);

		int active = spec.isRainbow() ? spec.previewColor() : spec.getSolidColor();
		int r = (active >> 16) & 0xFF;
		int g = (active >> 8) & 0xFF;
		int b = active & 0xFF;
		if (spec.isRainbow()) {
			drawRgbSlider(gfx, 0, "R", r, 0xFF553333, 0xFF221111);
			drawRgbSlider(gfx, 1, "G", g, 0xFF335533, 0xFF112211);
			drawRgbSlider(gfx, 2, "B", b, 0xFF333355, 0xFF111122);
		} else {
			drawRgbSlider(gfx, 0, "R", r, 0xFFAA3333, 0xFF661111);
			drawRgbSlider(gfx, 1, "G", g, 0xFF33AA33, 0xFF116611);
			drawRgbSlider(gfx, 2, "B", b, 0xFF3333AA, 0xFF111166);
		}

		int presetLabelY = sliderY0 + 3 * (SLIDER_H + SLIDER_GAP) + 4;
		gfx.centeredText(this.font, Component.translatable("glowblock.gui.color.presets"), cx, presetLabelY, 0xFFAAAAAA);

		super.extractRenderState(gfx, mouseX, mouseY, delta);

		int presetCount = PRESETS.length + 1;
		int presetSize = 20;
		int presetGap = 4;
		int totalPresetsW = presetCount * presetSize + (presetCount - 1) * presetGap;
		int presetStartX = cx - totalPresetsW / 2;

		for (int i = 0; i < PRESETS.length; i++) {
			int px = presetStartX + i * (presetSize + presetGap);
			gfx.fill(px + 2, presetY + 2, px + presetSize - 2, presetY + presetSize - 2, PRESETS[i]);

			if (!spec.isRainbow() && (spec.getSolidColor() & 0x00FFFFFF) == (PRESETS[i] & 0x00FFFFFF)) {
				gfx.outline(px - 1, presetY - 1, presetSize + 2, presetSize + 2, 0xFFFFFFFF);
			}
		}

		int rainbowPx = presetStartX + PRESETS.length * (presetSize + presetGap);
		drawRainbowSwatch(gfx, rainbowPx + 2, presetY + 2, presetSize - 4, presetSize - 4);
		if (spec.isRainbow()) {
			gfx.outline(rainbowPx - 1, presetY - 1, presetSize + 2, presetSize + 2, 0xFFFFFFFF);
		}
	}

	private void drawRainbowSwatch(GuiGraphicsExtractor gfx, int x, int y, int w, int h) {
		float tShift = (System.currentTimeMillis() % 4000L) / 4000.0f;
		for (int i = 0; i < w; i++) {
			float hue = ((float) i / w + tShift) % 1f;
			int rgb = hsvToRgb(hue);
			gfx.fill(x + i, y, x + i + 1, y + h, 0xFF000000 | rgb);
		}
	}

	private static int hsvToRgb(float h) {
		float r, g, b;
		int i = (int) Math.floor(h * 6f);
		float f = h * 6f - i;
		float v = 1f;
		float p = 0f;
		float q = 1f - f;
		float t = f;
		switch (Math.floorMod(i, 6)) {
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			case 5 -> { r = v; g = p; b = q; }
			default -> { r = v; g = t; b = p; }
		}
		int ri = Math.round(r * 255f) & 0xFF;
		int gi = Math.round(g * 255f) & 0xFF;
		int bi = Math.round(b * 255f) & 0xFF;
		return (ri << 16) | (gi << 8) | bi;
	}

	private void drawRgbSlider(GuiGraphicsExtractor gfx, int index, String label, int value,
	                           int fillColor, int trackColor) {
		int y = sliderY0 + index * (SLIDER_H + SLIDER_GAP);
		float frac = value / 255f;
		String autoSuffix = spec.isRainbow() ? Component.translatable("glowblock.gui.color.auto").getString() : "";
		String text = label + ": " + value + autoSuffix;
		drawSliderAt(gfx, y, frac, fillColor, trackColor, text);
	}

	private void drawSliderAt(GuiGraphicsExtractor gfx, int y, float frac,
	                          int fillColor, int trackColor, String text) {
		frac = Mth.clamp(frac, 0f, 1f);
		gfx.fill(sliderX, y, sliderX + sliderW, y + SLIDER_H, trackColor);
		gfx.outline(sliderX, y, sliderW, SLIDER_H, 0xFF222222);

		int fillW = (int) (frac * (sliderW - HANDLE_W));
		if (fillW > 0) {
			gfx.fill(sliderX + 1, y + 1, sliderX + 1 + fillW, y + SLIDER_H - 1, fillColor);
		}

		int handleX = sliderX + (int) (frac * (sliderW - HANDLE_W));
		gfx.fill(handleX, y, handleX + HANDLE_W, y + SLIDER_H, 0xFFCCCCCC);
		gfx.outline(handleX, y, HANDLE_W, SLIDER_H, 0xFF888888);

		gfx.centeredText(this.font, Component.literal(text),
				sliderX + sliderW / 2, y + (SLIDER_H - 8) / 2, 0xFFFFFFFF);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.buttonInfo().button() == 0 && !spec.isRainbow()) {
			for (int i = 0; i < 3; i++) {
				int y = sliderY0 + i * (SLIDER_H + SLIDER_GAP);
				if (event.x() >= sliderX && event.x() <= sliderX + sliderW
						&& event.y() >= y && event.y() <= y + SLIDER_H) {
					draggingSlider = i;
					updateSliderFromMouse(event.x());
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (draggingSlider >= 0) {
			updateSliderFromMouse(event.x());
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (draggingSlider >= 0) {
			draggingSlider = -1;
			return true;
		}
		return super.mouseReleased(event);
	}

	private void updateSliderFromMouse(double mx) {
		if (spec.isRainbow()) return;
		float frac = Mth.clamp((float) ((mx - sliderX) / sliderW), 0f, 1f);
		int val = Math.round(frac * 255);
		int cur = spec.getSolidColor();
		int r = (cur >> 16) & 0xFF;
		int g = (cur >> 8) & 0xFF;
		int b = cur & 0xFF;
		switch (draggingSlider) {
			case 0 -> r = val;
			case 1 -> g = val;
			case 2 -> b = val;
		}
		int newArgb = 0xFF000000 | (r << 16) | (g << 8) | b;
		spec.setSolidColor(newArgb);
		if (hexField != null) hexField.setValue(toHex(newArgb));
		pushChange();
	}

	private void onHexChanged(String text) {
		if (spec.isRainbow()) return;
		String hex = text.startsWith("#") ? text.substring(1) : text;
		if (hex.length() == 6) {
			try {
				int rgb = Integer.parseUnsignedInt(hex, 16);
				int argb = 0xFF000000 | rgb;
				spec.setSolidColor(argb);
				pushChange();
			} catch (NumberFormatException ignored) {}
		}
	}

	private void pushChange() {
		onSpecChanged.accept(spec.copy());
	}

	private String toHex(int argb) {
		return String.format("#%02X%02X%02X",
				(argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF);
	}

	@Override
	public void onClose() {
		pushChange();
		GlowConfig.markDirty();
		this.minecraft.setScreenAndShow(parent);
	}

	@Override
	public boolean isPauseScreen() { return false; }
}
