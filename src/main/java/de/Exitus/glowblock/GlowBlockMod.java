package de.Exitus.glowblock;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(GlowBlockMod.MOD_ID)
public class GlowBlockMod {
	public static final String MOD_ID = "glowblock";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);

	public static final DeferredBlock<Block> GLOWING_BLOCK = BLOCKS.registerBlock(
			"glowing_block",
			Block::new,
			() -> BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)
					.lightLevel(state -> 15)
	);

	public static final DeferredItem<BlockItem> GLOWING_BLOCK_ITEM = ITEMS.registerItem(
			"glowing_block",
			props -> new BlockItem(GLOWING_BLOCK.get(), props),
			() -> new Item.Properties().useBlockDescriptionPrefix()
	);

	public GlowBlockMod(IEventBus modBus) {
		BLOCKS.register(modBus);
		ITEMS.register(modBus);
		modBus.addListener(this::addToCreativeTab);
		LOGGER.info("GlowBlock mod constructor — glowing_block registered.");
	}

	private void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
		if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
			event.accept(GLOWING_BLOCK_ITEM.get());
		}
	}
}