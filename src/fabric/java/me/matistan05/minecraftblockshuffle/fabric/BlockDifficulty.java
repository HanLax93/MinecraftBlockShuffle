package me.matistan05.minecraftblockshuffle.fabric;

import net.minecraft.resources.Identifier;

import java.util.Set;

enum BlockDifficulty {
    EASY("easy", 0),
    NORMAL("normal", 1),
    HARD("hard", 2);

    private static final Set<String> HARD_BLOCKS = Set.of(
        "ancient_debris", "beacon", "conduit", "crying_obsidian", "diamond_block",
        "emerald_block", "ender_chest", "gilded_blackstone", "gold_block", "lodestone",
        "netherite_block", "obsidian", "respawn_anchor", "sculk_catalyst", "sculk_shrieker",
        "sponge", "wet_sponge", "turtle_egg", "dragon_egg", "heavy_core"
    );

    private static final String[] NORMAL_MARKERS = {
        "_ore", "copper", "deepslate", "amethyst", "glass", "terracotta", "concrete",
        "glazed", "prismarine", "sea_lantern", "magma", "nether", "crimson", "warped",
        "blackstone", "basalt", "quartz", "soul_", "end_stone", "purpur", "coral",
        "honey", "slime", "bookshelf", "lantern", "redstone", "lapis", "iron_block"
    };

    private final String id;
    private final int level;

    BlockDifficulty(String id, int level) {
        this.id = id;
        this.level = level;
    }

    String id() {
        return id;
    }

    boolean allows(Identifier blockId) {
        return classify(blockId).level <= level;
    }

    static BlockDifficulty parse(String value) {
        if (value != null) {
            for (BlockDifficulty difficulty : values()) {
                if (difficulty.id.equalsIgnoreCase(value)) return difficulty;
            }
        }
        return NORMAL;
    }

    static BlockDifficulty classify(Identifier blockId) {
        String path = blockId.getPath();
        if (HARD_BLOCKS.contains(path) || path.startsWith("waxed_")) return HARD;
        for (String marker : NORMAL_MARKERS) {
            if (path.contains(marker)) return NORMAL;
        }
        return EASY;
    }
}
