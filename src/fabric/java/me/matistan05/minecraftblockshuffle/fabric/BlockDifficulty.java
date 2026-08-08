package me.matistan05.minecraftblockshuffle.fabric;

enum BlockDifficulty {
    EASY("easy", 0),
    NORMAL("normal", 1),
    HARD("hard", 2);

    private final String id;
    private final int level;

    BlockDifficulty(String id, int level) {
        this.id = id;
        this.level = level;
    }

    String id() {
        return id;
    }

    boolean allows(BlockDifficulty targetDifficulty) {
        return targetDifficulty.level <= level;
    }

    static BlockDifficulty parse(String value) {
        if (value != null) {
            for (BlockDifficulty difficulty : values()) {
                if (difficulty.id.equalsIgnoreCase(value)) return difficulty;
            }
        }
        return NORMAL;
    }
}
