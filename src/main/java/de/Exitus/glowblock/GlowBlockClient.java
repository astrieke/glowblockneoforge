package de.Exitus.glowblock;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

@Mod(value = GlowBlockMod.MOD_ID, dist = Dist.CLIENT)
public class GlowBlockClient {

	private static final KeyMapping OPEN_GUI_KEY;

	static {
		KeyMapping.Category glowCategory =
				KeyMapping.Category.register(Identifier.tryParse("glowblock:glowblock"));

		OPEN_GUI_KEY = new KeyMapping(
				"key.glowblock.gui",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_G,
				glowCategory
		);
	}

	public GlowBlockClient(IEventBus modBus) {
		modBus.addListener(this::registerKeyMappings);

		GlowCommand.register();
		GlowVision.register();
		GlowInvalidation.register();
		GlowRenderer.register();
		GlowConfig.load();

		NeoForge.EVENT_BUS.addListener(this::onClientTick);

		Runtime.getRuntime().addShutdownHook(new Thread(GlowConfig::saveNow));

		GlowBlockMod.LOGGER.info(
				"GlowBlock client initialized — /lille gui + [G] key active.");
	}

	private void registerKeyMappings(RegisterKeyMappingsEvent event) {
		event.register(OPEN_GUI_KEY);
	}

	private void onClientTick(ClientTickEvent.Post event) {
		Minecraft client = Minecraft.getInstance();
		GlowConfig.tick();

		while (OPEN_GUI_KEY.consumeClick()) {
			client.setScreenAndShow(new GlowConfigScreen());
		}
	}

	public static KeyMapping getOpenGuiKey() {
		return OPEN_GUI_KEY;
	}
}