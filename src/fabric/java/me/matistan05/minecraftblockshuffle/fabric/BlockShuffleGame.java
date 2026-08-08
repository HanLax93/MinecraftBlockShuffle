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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.HashSet;
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
    private final ArrayDeque<Identifier> recentTargets = new ArrayDeque<>();
    private final ArrayDeque<String> recentFamilies = new ArrayDeque<>();
    private final Set<UUID> skipVotes = new HashSet<>();
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
        skipVotes.remove(player.getUUID());
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
            if (player != null && isStandingOnTarget(player, state.target)) {
                state.foundBlock = true;
                if (config.gameMode == 1) state.points++;
                BlockPos feet = player.blockPosition();
                BlockShuffleMod.LOGGER.info("target_complete player={} round={} target={} position={},{},{} elapsed_seconds={}",
                    state.name, round, blockId(state.target), feet.getX(), feet.getY(), feet.getZ(), ticks / 20);
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
        players.values().stream().filter(state -> state.stillPlaying && !state.foundBlock && state.target != null)
            .forEach(state -> logFailure(server, state));
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
        skipVotes.clear();
        sharedTarget = config.sameBlockForEveryone ? randomBlock() : null;
        players.values().stream().filter(state -> state.stillPlaying).forEach(state -> {
            state.foundBlock = false;
            assignTarget(server, state);
        });
        teleportForRound(server);
    }

    void skip(MinecraftServer server) {
        if (!running) throw new IllegalStateException("No Block Shuffle game is running");
        players.values().stream().filter(state -> state.stillPlaying && !state.foundBlock && state.target != null)
            .forEach(state -> BlockShuffleMod.LOGGER.info(
                "target_skipped player={} round={} target={} reason=vote_or_console", state.name, round,
                blockId(state.target)));
        players.values().forEach(state -> state.foundBlock = true);
        finishRound(server);
    }

    Component voteSkip(MinecraftServer server, ServerPlayer voter) {
        if (!running) throw new IllegalStateException("No Block Shuffle game is running");
        PlayerState voterState = players.get(voter.getUUID());
        if (voterState == null || !voterState.stillPlaying) throw new IllegalStateException("You are not an active player");
        skipVotes.add(voter.getUUID());
        int active = (int) players.values().stream()
            .filter(state -> state.stillPlaying && server.getPlayerList().getPlayer(state.id) != null)
            .count();
        int required = requiredSkipVotes(active);
        int votes = skipVotes.size();
        if (votes >= required) {
            skip(server);
            return Component.literal("Skip vote passed (" + votes + "/" + required + ").");
        }
        return Component.literal("Skip vote recorded (" + votes + "/" + required + ").");
    }

    static int requiredSkipVotes(int activePlayers) {
        return Math.max(1, activePlayers / 2 + 1);
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
        recentTargets.clear();
        recentFamilies.clear();
        skipVotes.clear();
        if (announce) broadcast(server, Component.literal("Block Shuffle reset.").withStyle(ChatFormatting.YELLOW));
    }

    Component status() {
        if (players.isEmpty()) return Component.literal("No players selected.");
        int secondsLeft = running ? Math.max(0, config.roundSeconds - ticks / 20) : 0;
        String text = players.values().stream().map(state -> state.name + (running
                ? "=" + state.points + "pts" + (state.foundBlock ? " (found)" : "") : ""))
            .reduce((left, right) -> left + ", " + right).orElse("");
        return Component.literal((running ? "Round " + round + " | " + secondsLeft + "s | " : "")
            + "Players: " + text);
    }

    Component target(ServerPlayer player) {
        PlayerState state = players.get(player.getUUID());
        if (!running || state == null || !state.stillPlaying || state.target == null) {
            return Component.literal("You do not have an active Block Shuffle target.");
        }
        return Component.literal("Round " + round + " target: ").append(targetName(state.target))
            .append(Component.literal(" [" + blockId(state.target) + "] | "
                + Math.max(0, config.roundSeconds - ticks / 20) + "s remaining"));
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
        rememberTarget(state.target);
        ServerPlayer player = server.getPlayerList().getPlayer(state.id);
        if (player != null) player.sendSystemMessage(Component.literal("Round " + round + ": stand on ")
            .append(targetName(state.target))
            .withStyle(ChatFormatting.DARK_GREEN));
        BlockShuffleMod.LOGGER.info("target_assigned player={} round={} target={} difficulty={} family={}", state.name,
            round, blockId(state.target), effectiveDifficulty().id(), TargetCatalog.forBlock(state.target).family());
    }

    private Block randomBlock() {
        BlockDifficulty maximum = effectiveDifficulty();
        List<TargetCatalog.Target> candidates = TargetCatalog.selectable(maximum, config.enableNetherBlocks,
            config.enableEndBlocks, config.bannedBlocks, recentTargets, recentFamilies);
        if (candidates.isEmpty()) throw new IllegalStateException("No eligible blocks remain");
        return candidates.get(random.nextInt(candidates.size())).block();
    }

    private BlockDifficulty effectiveDifficulty() {
        return difficultyForRound(config.difficulty(), round);
    }

    static BlockDifficulty difficultyForRound(BlockDifficulty configured, int round) {
        return switch (configured) {
            case EASY -> BlockDifficulty.EASY;
            case NORMAL -> round <= 3 ? BlockDifficulty.EASY : round <= 7 ? BlockDifficulty.NORMAL : BlockDifficulty.HARD;
            case HARD -> round <= 2 ? BlockDifficulty.EASY : round <= 5 ? BlockDifficulty.NORMAL : BlockDifficulty.HARD;
        };
    }

    private void rememberTarget(Block block) {
        TargetCatalog.Target target = TargetCatalog.forBlock(block);
        if (target == null) return;
        if (!recentTargets.isEmpty() && recentTargets.getLast().equals(target.id())) return;
        recentTargets.addLast(target.id());
        while (recentTargets.size() > 12) recentTargets.removeFirst();
        recentFamilies.addLast(target.family());
        while (recentFamilies.size() > 6) recentFamilies.removeFirst();
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
            List<Block> targets = active.stream().map(player -> players.get(player.getUUID()).target).toList();
            BlockPos destination = findSafeDestination(level, active.getFirst().blockPosition(), targets);
            if (destination != null) {
                for (int index = 0; index < active.size(); index++) {
                    teleport(active.get(index), level, offsetDestination(level, destination, index));
                }
            } else {
                for (ServerPlayer player : active) {
                    BlockPos fallback = fallbackPlayerDestination(level, player.blockPosition());
                    if (fallback != null) {
                        teleport(player, level, fallback);
                    }
                }
            }
        } else {
            active.forEach(player -> {
                BlockPos destination = findSafeDestination(level, player.blockPosition(),
                    List.of(players.get(player.getUUID()).target));
                if (destination != null) teleport(player, level, destination);
                else {
                    BlockPos fallback = fallbackPlayerDestination(level, player.blockPosition());
                    if (fallback != null) teleport(player, level, fallback);
                }
            });
        }
    }

    private BlockPos findSafeDestination(ServerLevel level, BlockPos center, List<Block> targets) {
        int primaryRadius = Math.max(1, config.randomTeleportRadius);
        int fallbackRadius = Math.min(128, primaryRadius * 3);
        BlockPos best = bestSafeDestination(level, center, targets, primaryRadius, 12);
        if (best != null) {
            int bestScore = destinationScore(level, best, targets);
            BlockShuffleMod.LOGGER.info("teleport_destination round={} position={},{},{} resource_score={}",
                round, best.getX(), best.getY(), best.getZ(), bestScore);
            return best;
        }

        if (fallbackRadius >= primaryRadius) {
            BlockPos fallback = bestSafeDestination(level, center, targets, fallbackRadius, 128);
            if (fallback != null) {
                int fallbackScore = destinationScore(level, fallback, targets);
                BlockShuffleMod.LOGGER.warn("Using fallback teleport search radius {} for round {}", fallbackRadius, round);
                BlockShuffleMod.LOGGER.info("teleport_destination round={} position={},{},{} resource_score={}",
                    round, fallback.getX(), fallback.getY(), fallback.getZ(), fallbackScore);
                return fallback;
            }
        }

        BlockShuffleMod.LOGGER.warn("Could not find a safe random teleport destination for round {}", round);
        return null;
    }

    private BlockPos bestSafeDestination(ServerLevel level, BlockPos center, List<Block> targets, int radius, int attempts) {
        BlockPos best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int attempt = 0; attempt < attempts; attempt++) {
            int x = center.getX() + random.nextInt(-radius, radius + 1);
            int z = center.getZ() + random.nextInt(-radius, radius + 1);
            if (!level.getWorldBorder().isWithinBounds(x, z)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos feet = new BlockPos(x, y, z);
            if (!isSafeFeet(level, feet)) continue;
            int score = destinationScore(level, feet, targets);
            if (best == null || score > bestScore) {
                best = feet;
                bestScore = score;
            }
            if (score >= 100) break;
        }
        return best;
    }

    private BlockPos fallbackPlayerDestination(ServerLevel level, BlockPos center) {
        if (isSafeFeet(level, center)) return center;
        int fallbackRadius = Math.max(1, config.randomTeleportRadius);
        for (int attempt = 0; attempt < 64; attempt++) {
            int x = center.getX() + random.nextInt(-fallbackRadius, fallbackRadius + 1);
            int z = center.getZ() + random.nextInt(-fallbackRadius, fallbackRadius + 1);
            if (!level.getWorldBorder().isWithinBounds(x, z)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos candidate = new BlockPos(x, y, z);
            if (isSafeFeet(level, candidate)) return candidate;
        }
        return null;
    }

    private int destinationScore(ServerLevel level, BlockPos center, List<Block> targets) {
        Set<Block> exactTargets = new HashSet<>(targets);
        Set<String> resources = new HashSet<>();
        for (Block targetBlock : targets) {
            TargetCatalog.Target target = TargetCatalog.forBlock(targetBlock);
            if (target != null) resources.addAll(target.nearbyResources());
        }
        int score = 0;
        for (int dx = -8; dx <= 8; dx += 8) {
            for (int dz = -8; dz <= 8; dz += 8) {
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
                for (int depth = 0; depth <= 8; depth++) {
                    Block nearby = level.getBlockState(new BlockPos(x, surfaceY - depth, z)).getBlock();
                    if (exactTargets.contains(nearby)) score += 100;
                    if (resources.contains(BuiltInRegistries.BLOCK.getKey(nearby).getPath())) score += 10;
                }
            }
        }
        return score;
    }

    private BlockPos offsetDestination(ServerLevel level, BlockPos origin, int playerIndex) {
        if (playerIndex == 0) return origin;
        int[][] offsets = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 2}, {-2, -2}, {2, -2}, {-2, 2}};
        for (int step = 0; step < offsets.length; step++) {
            int[] offset = offsets[(playerIndex - 1 + step) % offsets.length];
            int x = origin.getX() + offset[0];
            int z = origin.getZ() + offset[1];
            BlockPos candidate = new BlockPos(x,
                level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (isSafeFeet(level, candidate)) return candidate;
        }
        return origin;
    }

    private boolean isSafeFeet(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet.below()).isSolid()
            && level.getBlockState(feet).isAir()
            && level.getBlockState(feet.above()).isAir()
            && level.getFluidState(feet).isEmpty()
            && level.getFluidState(feet.below()).isEmpty();
    }

    private void teleport(ServerPlayer player, ServerLevel level, BlockPos destination) {
        player.teleportTo(level, destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5,
            Set.<Relative>of(), player.getYRot(), player.getXRot(), true);
        player.invulnerableTime = 100;
        BlockShuffleMod.LOGGER.info("player_teleported player={} round={} position={},{},{} mode={}",
            player.getGameProfile().name(), round, destination.getX(), destination.getY(), destination.getZ(),
            config.randomTeleportMode);
        player.sendSystemMessage(Component.literal("Randomly teleported for round " + round)
            .withStyle(ChatFormatting.AQUA));
    }

    private static Component targetName(Block block) {
        return Component.translatable(block.getDescriptionId()).withStyle(ChatFormatting.YELLOW);
    }

    private boolean isStandingOnTarget(ServerPlayer player, Block target) {
        AABB box = player.getBoundingBox();
        double inset = 0.001;
        double y = box.minY - 0.01;
        double[] xs = {box.minX + inset, (box.minX + box.maxX) / 2.0, box.maxX - inset};
        double[] zs = {box.minZ + inset, (box.minZ + box.maxZ) / 2.0, box.maxZ - inset};
        for (double x : xs) {
            for (double z : zs) {
                if (player.level().getBlockState(BlockPos.containing(x, y, z)).is(target)) return true;
            }
        }
        return false;
    }

    private void logFailure(MinecraftServer server, PlayerState state) {
        ServerPlayer player = server.getPlayerList().getPlayer(state.id);
        if (player == null) {
            BlockShuffleMod.LOGGER.info("target_failed player={} round={} target={} reason=offline", state.name,
                round, blockId(state.target));
            return;
        }
        BlockPos position = player.blockPosition();
        Block below = player.level().getBlockState(position.below()).getBlock();
        BlockShuffleMod.LOGGER.info(
            "target_failed player={} round={} target={} below={} position={},{},{} precise_y={} still_playing={} elapsed_seconds={}",
            state.name, round, blockId(state.target), blockId(below), position.getX(), position.getY(), position.getZ(),
            player.getY(), state.stillPlaying, ticks / 20);
    }

    private static String blockId(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    private void broadcast(MinecraftServer server, Component message) {
        players.keySet().forEach(id -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) player.sendSystemMessage(message);
        });
    }
}
