package de.Exitus.glowblock;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class GlowColorSpec {
	public enum Mode { SOLID, RAINBOW }

	public static final float RAINBOW_SPEED = 0.25f;

	private Mode mode;
	private int color;

	private GlowColorSpec(Mode mode, int color) {
		this.mode = mode;
		this.color = ensureAlpha(color);
	}

	public static GlowColorSpec solid(int argb) {
		return new GlowColorSpec(Mode.SOLID, argb);
	}

	public static GlowColorSpec rainbow() {
		return new GlowColorSpec(Mode.RAINBOW, GlowColors.DEFAULT);
	}

	public boolean isRainbow() { return mode == Mode.RAINBOW; }

	public int getSolidColor() { return color; }

	public void setSolidColor(int argb) {
		this.color = ensureAlpha(argb);
		this.mode = Mode.SOLID;
	}

	public void setRainbow() {
		this.mode = Mode.RAINBOW;
	}

	public int currentColor(float timeSeconds) {
		if (mode == Mode.RAINBOW) {
			return rainbowColor(timeSeconds);
		}
		return color;
	}

	public GlowColorSpec copy() {
		return new GlowColorSpec(mode, color);
	}

	private static int rainbowColor(float timeSeconds) {
		float hue = (timeSeconds * RAINBOW_SPEED) % 1f;
		if (hue < 0f) hue += 1f;
		return 0xFF000000 | hsvToRgbInt(hue);
	}

	public int previewColor() {
		if (mode == Mode.RAINBOW) {
			return rainbowColor(System.currentTimeMillis() / 1000.0f);
		}
		return color;
	}

	private static int hsvToRgbInt(float h) {
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

	private static int ensureAlpha(int argb) {
		return 0xFF000000 | (argb & 0x00FFFFFF);
	}

	public JsonElement toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("mode", mode.name().toLowerCase());
		if (mode == Mode.SOLID) {
			o.addProperty("color", String.format("%08X", color));
		}
		return o;
	}

	public static GlowColorSpec fromJson(JsonElement el) {
		if (el == null || el.isJsonNull()) return solid(GlowColors.DEFAULT);

		if (el.isJsonPrimitive()) {
			try {
				int c = (int) Long.parseUnsignedLong(el.getAsString(), 16);
				return solid(c);
			} catch (NumberFormatException e) {
				return solid(GlowColors.DEFAULT);
			}
		}

		if (!el.isJsonObject()) return solid(GlowColors.DEFAULT);
		JsonObject o = el.getAsJsonObject();

		Mode mode = Mode.SOLID;
		if (o.has("mode")) {
			try {
				String m = o.get("mode").getAsString().trim().toUpperCase();
				if (m.equals("RAINBOW")) mode = Mode.RAINBOW;
			} catch (Exception ignored) {}
		}

		if (mode == Mode.RAINBOW) return rainbow();

		int color = GlowColors.DEFAULT;
		if (o.has("color")) {
			try {
				color = (int) Long.parseUnsignedLong(o.get("color").getAsString(), 16);
			} catch (Exception ignored) {}
		} else if (o.has("colors") && o.get("colors").isJsonArray() && !o.getAsJsonArray("colors").isEmpty()) {
			try {
				color = (int) Long.parseUnsignedLong(o.getAsJsonArray("colors").get(0).getAsString(), 16);
			} catch (Exception ignored) {}
		}
		return solid(color);
	}
}
