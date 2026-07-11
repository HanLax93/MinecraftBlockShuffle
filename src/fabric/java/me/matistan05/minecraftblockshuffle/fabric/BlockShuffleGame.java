package me.matistan05.minecraftblockshuffle.fabric;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

final class BlockShuffleGame {
    private final BlockShuffleConfig config;
    private final Map<UUID, PlayerState> players = new LinkedHashMap<>();
    private final Random random = new Random();
    private boolean running;
    private int ticks;
    private int round = 1;
    private Block sharedTarget;

    BlockShuffleGame(BlockShuffleConfig config) {
        this.config = config;
    }

    boolean isRunning() { return running; }

    void add(ServerPlayer player) {
        players.putIfAbsent(player.getUUID(), new PlayerState(player));
    }

    void remove(ServerPlayer player) {
        PlayerState state = players.get(player.getUUID());
        if (state == null) return;
        if (running) {
            state.stillPlaying = false;
            if (config.giveSpectators) player.setGameMode(GameType.SPECTATOR);
        } else {
            players.remove(player.getUUID());
        }
    }

    void start(MinecraftServer server) {
        if (running) throw new IllegalStateException("A Block Shuffle game is already running");
        if (config.playWithEveryone) {
            players.clear();
            server.getPlayerList().getPlayers().forEach(this::add);
        }
        if (players.isEmpty()) throw new IllegalStateException("Add at least one online player first");
        running = true;
        ticks = 0;
        round = 1;
        sharedTarget = config.sameBlockForEveryone ? randomBlock() : null;
        players.values().forEach(state -> preparePlayer(server, state));
        teleportForRound(server);
        broadcast(server, Component.literal("Block Shuffle started!").withStyle(ChatFormatting.GREEN));
    }

    void tick(MinecraftServer server) {
        if (!running) return;
        ticks++;
        for (PlayerState state : players.values()) {
            if (!state.stillPlaying || state.foundBlock || state.target == null) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(state.id);
            if (player != null && player.level().getBlockState(player.blockPosition().below()).is(state.target)) {
                state.foundBlock = true;
                if (config.gameMode == 1) state.points++;
                player.sendSystemMessage(Component.literal("Target found!").withStyle(ChatFormatting.GREEN));
                if (config.onlyFirstPoint && config.gameMode == 1) finishRound(server);
            }
        }
        if (!running) return;
        int roundTicks = config.roundSeconds * 20;
        if (players.values().stream().filter(state -> state.stillPlaying).allMatch(state -> state.foundBlock)
            || ticks >= roundTicks) {
            finishRound(server);
        } else if (ticks % 20 == 0) {
            int secondsLeft = Math.max(0, config.roundSeconds - ticks / 20);
            players.values().forEach(state -> {
                ServerPlayer player = server.getPlayerList().getPlayer(state.id);
                if (player != null && state.stillPlaying && state.target != null) {
                    player.displayClientMessage(Component.literal("Round " + round + " | ")
                        .append(targetName(state.target))
                        .append(Component.literal(" | " + secondsLeft + "s | " + state.points + " pts")), true);
                }
            });
        }
    }

    private void finishRound(MinecraftServer server) {
        if (!running) return;
        if (config.gameMode == 0) {
            players.values().stream().filter(state -> state.stillPlaying && !state.foundBlock).forEach(state -> {
                state.stillPlaying = false;
                ServerPlayer player = server.getPlayerList().getPlayer(state.id);
                if (player != null && config.giveSpectators) player.setGameMode(GameType.SPECTATOR);
            });
            List<PlayerState> remaining = players.values().stream().filter(state -> state.stillPlaying).toList();
            if (remaining.size() <= 1) {
                broadcast(server, Component.literal(remaining.isEmpty() ? "Nobody won." : remaining.getFirst().name + " won!")
                    .withStyle(ChatFormatting.GOLD));
                reset(server, true);
                return;
            }
        } else {
            List<PlayerState> winners = players.values().stream().filter(state -> state.points >= config.pointsToWin).toList();
            if (!winners.isEmpty()) {
                broadcast(server, Component.literal("Winner: " + winners.getFirst().name).withStyle(ChatFormatting.GOLD));
                reset(server, true);
                return;
            }
        }
        round++;
        ticks = 0;
        sharedTarget = config.sameBlockForEveryone ? randomBlock() : null;
        teleportForRound(server);
        players.values().stream().filter(state -> state.stillPlaying).forEach(state -> {
            state.foundBlock = false;
            assignTarget(server, state);
        });
    }

    void skip(MinecraftServer server) {
        if (!running) throw new IllegalStateException("No Block Shuffle game is running");
        players.values().forEach(state -> state.foundBlock = true);
        finishRound(server);
    }

    void reset(MinecraftServer server, boolean announce) {
        if (running) {
            players.values().forEach(state -> {
                ServerPlayer player = server.getPlayerList().getPlayer(state.id);
                if (player != null && state.previousGameMode != null) player.setGameMode(state.previousGameMode);
            });
        }
        running = false;
        ticks = 0;
        round = 1;
        players.clear();
        if (announce) broadcast(server, Component.literal("Block Shuffle reset.").withStyle(ChatFormatting.YELLOW));
    }

    Component status() {
        if (players.isEmpty()) return Component.literal("No players selected.");
        String text = players.values().stream().map(state -> state.name + (running ? "=" + state.points : ""))
            .reduce((left, right) -> left + ", " + right).orElse("");
        return Component.literal("Block Shuffle players: " + text);
    }

    boolean ban(String id) {
        Identifier location = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
        if (location == null || !BuiltInRegistries.BLOCK.containsKey(location)) return false;
        config.bannedBlocks.add(location.toString());
        config.save();
        return true;
    }

    boolean unban(String id) {
        Identifier location = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
        if (location == null) return false;
        boolean removed = config.bannedBlocks.remove(location.toString());
        config.save();
        return removed;
    }

    String difficulty() {
        return config.difficulty().id();
    }

    String setDifficulty(String value) {
        BlockDifficulty difficulty = BlockDifficulty.parse(value);
        config.difficulty = difficulty.id();
        config.save();
        return difficulty.id();
    }

    String teleportMode() {
        return config.randomTeleportMode;
    }

    String setTeleportMode(String value) {
        config.randomTeleportMode = BlockShuffleConfig.normalizeTeleportMode(value);
        config.save();
        return config.randomTeleportMode;
    }

    private void preparePlayer(MinecraftServer server, PlayerState state) {
        ServerPlayer player = server.getPlayerList().getPlayer(state.id);
        if (player == null) return;
        state.previousGameMode = player.gameMode.getGameModeForPlayer();
        state.points = 0;
        state.foundBlock = false;
        state.stillPlaying = true;
        player.setGameMode(GameType.SURVIVAL);
        if (config.clearInventories) player.getInventory().clearContent();
        assignTarget(server, state);
    }

    private void assignTarget(MinecraftServer server, PlayerState state) {
        state.target = sharedTarget != null ? sharedTarget : randomBlock();
        ServerPlayer player = server.getPlayerList().getPlayer(state.id);
        if (player != null) player.sendSystemMessage(Component.literal("Round " + round + ": stand on ")
            .append(targetName(state.target))
            .withStyle(ChatFormatting.DARK_GREEN));
    }

    private Block randomBlock() {
        List<Block> candidates = new ArrayList<>();
        BuiltInRegistries.BLOCK.forEach(block -> {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            if (block == Blocks.AIR || config.bannedBlocks.contains(id.toString())) return;
            if (!config.difficulty().allows(id)) return;
            String path = id.getPath();
            if (!config.enableNetherBlocks && (path.contains("nether") || path.contains("crimson") || path.contains("warped")
                || path.contains("basalt") || path.contains("blackstone") || path.contains("soul") || path.contains("quartz"))) return;
            if (block.defaultBlockState().isSolid()) candidates.add(block);
        });
        if (candidates.isEmpty()) throw new IllegalStateException("No eligible blocks remain");
        return candidates.get(random.nextInt(candidates.size()));
    }

    private void teleportForRound(MinecraftServer server) {
        if (config.randomTeleportMode.equals("off")
            || (round - 1) % config.randomTeleportEveryRounds != 0) return;
        ServerLevel level = server.overworld();
        List<ServerPlayer> active = players.values().stream()
            .filter(state -> state.stillPlaying)
            .map(state -> server.getPlayerList().getPlayer(state.id))
            .filter(java.util.Objects::nonNull)
            .toList();
        if (active.isEmpty()) return;

        if (config.randomTeleportMode.equals("shared")) {
            BlockPos destination = findSafeDestination(level, active.getFirst().blockPosition());
            if (destination != null) active.forEach(player -> teleport(player, level, destination));
        } else {
            active.forEach(player -> {
                BlockPos destination = findSafeDestination(level, player.blockPosition());
                if (destination != null) teleport(player, level, destination);
            });
        }
    }

    private BlockPos findSafeDestination(ServerLevel level, BlockPos center) {
        for (int attempt = 0; attempt < 64; attempt++) {
            int x = center.getX() + random.nextInt(-config.randomTeleportRadius, config.randomTeleportRadius + 1);
            int z = center.getZ() + random.nextInt(-config.randomTeleportRadius, config.randomTeleportRadius + 1);
            if (!level.getWorldBorder().isWithinBounds(x, z)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos feet = new BlockPos(x, y, z);
            if (!level.getBlockState(feet.below()).isSolid()) continue;
            if (!level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()) continue;
            if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.below()).isEmpty()) continue;
            return feet;
        }
        BlockShuffleMod.LOGGER.warn("Could not find a safe random teleport destination for round {}", round);
        return null;
    }

    private void teleport(ServerPlayer player, ServerLevel level, BlockPos destination) {
        player.teleportTo(level, destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5,
            Set.<Relative>of(), player.getYRot(), player.getXRot(), true);
        player.invulnerableTime = 100;
        player.sendSystemMessage(Component.literal("Randomly teleported for round " + round)
            .withStyle(ChatFormatting.AQUA));
    }

    private static Component targetName(Block block) {
        return Component.translatable(block.getDescriptionId()).withStyle(ChatFormatting.YELLOW);
    }

    private void broadcast(MinecraftServer server, Component message) {
        players.keySet().forEach(id -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) player.sendSystemMessage(message);
        });
    }
}
