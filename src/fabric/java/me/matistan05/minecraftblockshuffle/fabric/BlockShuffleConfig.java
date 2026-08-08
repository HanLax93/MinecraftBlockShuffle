package me.matistan05.minecraftblockshuffle.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public final class BlockShuffleConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("minecraft-block-shuffle.json");

    public int roundSeconds = 300;
    public int gameMode = 1;
    public int pointsToWin = 5;
    public String difficulty = "normal";
    public String randomTeleportMode = "shared";
    public int randomTeleportEveryRounds = 3;
    public int randomTeleportRadius = 500;
    public boolean playWithEveryone = true;
    public boolean sameBlockForEveryone = false;
    public boolean enableNetherBlocks = false;
    public boolean enableEndBlocks = false;
    public boolean clearInventories = true;
    public boolean giveSpectators = true;
    public boolean onlyFirstPoint = false;
    public Set<String> bannedBlocks = new HashSet<>();

    public static BlockShuffleConfig load() {
        if (Files.exists(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH)) {
                BlockShuffleConfig config = GSON.fromJson(reader, BlockShuffleConfig.class);
                if (config != null) {
                    config.roundSeconds = Math.max(1, Math.min(3600, config.roundSeconds));
                    config.difficulty = BlockDifficulty.parse(config.difficulty).id();
                    config.randomTeleportMode = normalizeTeleportMode(config.randomTeleportMode);
                    config.randomTeleportEveryRounds = Math.max(1, config.randomTeleportEveryRounds);
                    config.randomTeleportRadius = Math.max(32, Math.min(10000, config.randomTeleportRadius));
                    if (config.bannedBlocks == null) config.bannedBlocks = new HashSet<>();
                    return config;
                }
            } catch (IOException | RuntimeException exception) {
                BlockShuffleMod.LOGGER.error("Could not read {}", PATH, exception);
            }
        }
        BlockShuffleConfig config = new BlockShuffleConfig();
        config.bannedBlocks.addAll(Set.of(
            "minecraft:barrier", "minecraft:bedrock", "minecraft:command_block",
            "minecraft:chain_command_block", "minecraft:repeating_command_block",
            "minecraft:end_portal_frame", "minecraft:jigsaw", "minecraft:light",
            "minecraft:spawner", "minecraft:structure_block", "minecraft:structure_void"
        ));
        config.save();
        return config;
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException exception) {
            BlockShuffleMod.LOGGER.error("Could not write {}", PATH, exception);
        }
    }

    public BlockDifficulty difficulty() {
        return BlockDifficulty.parse(difficulty);
    }

    public static String normalizeTeleportMode(String value) {
        if (value != null && (value.equalsIgnoreCase("off") || value.equalsIgnoreCase("shared")
            || value.equalsIgnoreCase("separate"))) return value.toLowerCase();
        return "shared";
    }
}
