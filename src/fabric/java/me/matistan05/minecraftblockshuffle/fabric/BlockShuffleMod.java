package me.matistan05.minecraftblockshuffle.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BlockShuffleMod implements ModInitializer {
    public static final String MOD_ID = "minecraftblockshuffle";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static BlockShuffleGame game;

    @Override
    public void onInitialize() {
        BlockShuffleConfig config = BlockShuffleConfig.load();
        game = new BlockShuffleGame(config);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            BlockShuffleCommands.register(dispatcher, game));
        ServerTickEvents.END_SERVER_TICK.register(game::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> game.reset(server, false));
        LOGGER.info("Minecraft Block Shuffle Fabric initialized for Minecraft 1.21.11");
    }
}
