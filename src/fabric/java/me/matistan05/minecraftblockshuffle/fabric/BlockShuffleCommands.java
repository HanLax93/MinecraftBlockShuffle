package me.matistan05.minecraftblockshuffle.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;

final class BlockShuffleCommands {
    private BlockShuffleCommands() {}

    static void register(CommandDispatcher<CommandSourceStack> dispatcher, BlockShuffleGame game) {
        dispatcher.register(Commands.literal("blockshuffle")
            .then(Commands.literal("add")
                .then(Commands.argument("players", EntityArgument.players()).executes(context -> {
                    for (var player : EntityArgument.getPlayers(context, "players")) game.add(player);
                    context.getSource().sendSuccess(() -> Component.literal("Players added."), false);
                    return 1;
                })))
            .then(Commands.literal("remove")
                .then(Commands.argument("players", EntityArgument.players()).executes(context -> {
                    for (var player : EntityArgument.getPlayers(context, "players")) game.remove(player);
                    context.getSource().sendSuccess(() -> Component.literal("Players removed."), false);
                    return 1;
                })))
            .then(Commands.literal("start").executes(context -> run(context.getSource(), () -> game.start(context.getSource().getServer()))))
            .then(Commands.literal("reset").executes(context -> {
                game.reset(context.getSource().getServer(), true);
                return 1;
            }))
            .then(Commands.literal("skip").executes(context -> run(context.getSource(), () -> game.skip(context.getSource().getServer()))))
            .then(Commands.literal("list").executes(context -> {
                context.getSource().sendSuccess(game::status, false);
                return 1;
            }))
            .then(Commands.literal("difficulty")
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal("Difficulty: " + game.difficulty()), false);
                    return 1;
                })
                .then(Commands.literal("easy").executes(context -> setDifficulty(context.getSource(), game, "easy")))
                .then(Commands.literal("normal").executes(context -> setDifficulty(context.getSource(), game, "normal")))
                .then(Commands.literal("hard").executes(context -> setDifficulty(context.getSource(), game, "hard"))))
            .then(Commands.literal("teleport")
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal("Random teleport mode: " + game.teleportMode()), false);
                    return 1;
                })
                .then(Commands.literal("off").executes(context -> setTeleportMode(context.getSource(), game, "off")))
                .then(Commands.literal("shared").executes(context -> setTeleportMode(context.getSource(), game, "shared")))
                .then(Commands.literal("separate").executes(context -> setTeleportMode(context.getSource(), game, "separate"))))
            .then(Commands.literal("ban")
                .then(Commands.argument("block", StringArgumentType.word()).executes(context -> {
                    String id = StringArgumentType.getString(context, "block");
                    if (!game.ban(id)) throw new IllegalArgumentException("Unknown block: " + id);
                    context.getSource().sendSuccess(() -> Component.literal("Banned " + id), false);
                    return 1;
                })))
            .then(Commands.literal("unban")
                .then(Commands.argument("block", StringArgumentType.word()).executes(context -> {
                    String id = StringArgumentType.getString(context, "block");
                    if (!game.unban(id)) throw new IllegalArgumentException("Block is not banned: " + id);
                    context.getSource().sendSuccess(() -> Component.literal("Unbanned " + id), false);
                    return 1;
                }))));
    }

    private static int run(CommandSourceStack source, Runnable action) {
        try {
            action.run();
            return 1;
        } catch (RuntimeException exception) {
            BlockShuffleMod.LOGGER.error("Block Shuffle command failed", exception);
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int setDifficulty(CommandSourceStack source, BlockShuffleGame game, String value) {
        String selected = game.setDifficulty(value);
        source.sendSuccess(() -> Component.literal("Block Shuffle difficulty set to " + selected), false);
        return 1;
    }

    private static int setTeleportMode(CommandSourceStack source, BlockShuffleGame game, String value) {
        String selected = game.setTeleportMode(value);
        source.sendSuccess(() -> Component.literal("Random teleport mode set to " + selected), false);
        return 1;
    }
}
