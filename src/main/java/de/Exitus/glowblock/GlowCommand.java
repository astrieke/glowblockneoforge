package de.Exitus.glowblock;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;

public final class GlowCommand {

	private static final SimpleCommandExceptionType NOT_TRACKED =
			new SimpleCommandExceptionType(Component.literal("Dieser Block wird nicht getrackt."));

	private static final SimpleCommandExceptionType UNKNOWN_BLOCK =
			new SimpleCommandExceptionType(Component.literal("Unbekannter Block."));

	private static final SuggestionProvider<CommandSourceStack> TRACKED_BLOCKS =
			(ctx, builder) -> {
				List<String> ids = new ArrayList<>();
				for (Block block : GlowState.getTrackedBlocks()) {
					Identifier id = BuiltInRegistries.BLOCK.getKey(block);
					if (id != null) ids.add(id.toString());
				}
				return SharedSuggestionProvider.suggest(ids, builder);
			};

	private GlowCommand() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(GlowCommand::onRegisterClientCommands);
	}

	private static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
		event.getDispatcher().register(
				Commands.literal("lille")
						.then(Commands.literal("gui")
								.executes(GlowCommand::openGui))
						.then(Commands.literal("clear")
								.executes(GlowCommand::clear))
						.then(Commands.literal("debug")
								.executes(GlowCommand::debug))
						.then(Commands.literal("add")
								.then(Commands.argument("block",
												BlockStateArgument.block(event.getBuildContext()))
										.executes(GlowCommand::addBlock)))
						.then(Commands.literal("remove")
								.then(Commands.argument("block",
												StringArgumentType.greedyString())
										.suggests(TRACKED_BLOCKS)
										.executes(GlowCommand::removeBlock)))
		);
	}

	private static int addBlock(CommandContext<CommandSourceStack> ctx) {
		BlockInput input = ctx.getArgument("block", BlockInput.class);
		Block block = input.getState().getBlock();
		GlowState.addTargetWithSavedColor(block);
		GlowVision.invalidateQueue();
		GlowConfig.markDirty();
		return 1;
	}

	private static int removeBlock(CommandContext<CommandSourceStack> ctx)
			throws CommandSyntaxException {
		String raw = StringArgumentType.getString(ctx, "block").trim();
		Identifier id = Identifier.tryParse(raw);
		if (id == null) throw UNKNOWN_BLOCK.create();

		Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
		if (block == null) throw UNKNOWN_BLOCK.create();

		if (!GlowState.removeTarget(block)) throw NOT_TRACKED.create();

		GlowVision.invalidateQueue();
		GlowConfig.markDirty();
		return 1;
	}

	private static int openGui(CommandContext<CommandSourceStack> ctx) {
		Minecraft.getInstance().execute(() ->
				Minecraft.getInstance().setScreenAndShow(new GlowConfigScreen()));
		return 1;
	}

	private static int clear(CommandContext<CommandSourceStack> ctx) {
		GlowState.reset();
		GlowVision.invalidateQueue();
		GlowConfig.markDirty();
		return 1;
	}

	private static int debug(CommandContext<CommandSourceStack> ctx) {
		GlowVision.toggleDebug();
		return 1;
	}
}
