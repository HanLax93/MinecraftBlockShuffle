package me.matistan05.minecraftblockshuffle.fabric;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;

import java.util.UUID;

final class PlayerState {
    final UUID id;
    final String name;
    int points;
    boolean foundBlock;
    boolean stillPlaying = true;
    Block target;
    GameType previousGameMode;

    PlayerState(ServerPlayer player) {
        this.id = player.getUUID();
        this.name = player.getGameProfile().name();
        this.previousGameMode = player.gameMode.getGameModeForPlayer();
    }
}
