package me.matistan05.minecraftblockshuffle.fabric;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collection;

/** A deliberately curated list: every entry must be obtainable and placeable in survival. */
final class TargetCatalog {
    enum Dimension { OVERWORLD, NETHER, END }

    record Target(Block block, Identifier id, BlockDifficulty difficulty, Dimension dimension,
                  String family, Set<String> nearbyResources) {}

    private static final List<Definition> DEFINITIONS = new ArrayList<>();
    private static final Map<Block, Target> BY_BLOCK = new HashMap<>();
    private static List<Target> resolved;

    static {
        add(BlockDifficulty.EASY, Dimension.OVERWORLD, "stone", "stone", "cobblestone", "gravel", "sand",
            "dirt", "grass_block");
        add(BlockDifficulty.EASY, Dimension.OVERWORLD, "oak", "oak_log", "oak_wood", "stripped_oak_log",
            "oak_planks", "oak_slab", "oak_stairs", "oak_fence", "oak_fence_gate", "oak_door",
            "oak_trapdoor", "oak_leaves");
        add(BlockDifficulty.EASY, Dimension.OVERWORLD, "birch", "birch_log", "birch_wood", "stripped_birch_log",
            "birch_planks", "birch_slab", "birch_stairs", "birch_fence", "birch_fence_gate", "birch_door",
            "birch_trapdoor", "birch_leaves");
        add(BlockDifficulty.EASY, Dimension.OVERWORLD, "craft-basic", "crafting_table", "furnace", "chest",
            "barrel");
        add(BlockDifficulty.EASY, Dimension.OVERWORLD, "stone-craft", "stone_slab", "stone_stairs",
            "cobblestone_slab", "cobblestone_stairs", "cobblestone_wall", "stone_bricks", "stone_brick_slab",
            "stone_brick_stairs", "stone_brick_wall");

        add(BlockDifficulty.NORMAL, Dimension.OVERWORLD, "geology", "granite", "diorite", "andesite",
            "coarse_dirt", "rooted_dirt", "mud", "clay", "moss_block", "calcite", "tuff",
            "dripstone_block", "cobbled_deepslate", "polished_andesite", "polished_diorite", "polished_granite");

        add(BlockDifficulty.NORMAL, Dimension.OVERWORLD, "spruce", "spruce_log", "spruce_planks", "spruce_slab",
            "spruce_stairs", "spruce_fence", "spruce_door", "spruce_trapdoor", "spruce_leaves");
        add(BlockDifficulty.NORMAL, Dimension.OVERWORLD, "jungle", "jungle_log", "jungle_planks", "jungle_slab",
            "jungle_stairs", "jungle_fence", "jungle_door", "jungle_trapdoor", "jungle_leaves");
        add(BlockDifficulty.NORMAL, Dimension.OVERWORLD, "acacia", "acacia_log", "acacia_planks", "acacia_slab",
            "acacia_stairs", "acacia_fence", "acacia_door", "acacia_trapdoor", "acacia_leaves");
        add(BlockDifficulty.NORMAL, Dimension.OVERWORLD, "dark_oak", "dark_oak_log", "dark_oak_planks",
            "dark_oak_slab", "dark_oak_stairs", "dark_oak_fence", "dark_oak_door", "dark_oak_trapdoor",
            "dark_oak_leaves");
        add(BlockDifficulty.NORMAL, Dimension.OVERWORLD, "cherry", "cherry_log", "cherry_planks", "cherry_slab",
            "cherry_stairs", "cherry_fence", "cherry_door", "cherry_trapdoor", "cherry_leaves");
        add(BlockDifficulty.NORMAL, Dimension.OVERWORLD, "bamboo", "bamboo_block", "bamboo_planks", "bamboo_slab",
            "bamboo_stairs", "bamboo_mosaic", "bamboo_door", "bamboo_trapdoor");
        add(BlockDifficulty.NORMAL, Dimension.OVERWORLD, "mangrove", "mangrove_log", "mangrove_planks",
            "mangrove_slab", "mangrove_stairs", "mangrove_fence", "mangrove_door", "mangrove_trapdoor",
            "mangrove_leaves", "mangrove_roots", "muddy_mangrove_roots");
        add(BlockDifficulty.NORMAL, Dimension.OVERWORLD, "iron", "iron_block", "iron_bars", "iron_door",
            "iron_trapdoor", "cauldron", "hopper");
        add(BlockDifficulty.NORMAL, Dimension.OVERWORLD, "copper", "copper_block", "cut_copper", "cut_copper_slab",
            "cut_copper_stairs", "lightning_rod");
        add(BlockDifficulty.NORMAL, Dimension.OVERWORLD, "redstone", "redstone_block", "piston",
            "dispenser", "dropper", "observer", "target", "note_block");
        add(BlockDifficulty.NORMAL, Dimension.OVERWORLD, "color", "red_wool", "yellow_wool", "blue_wool",
            "black_wool", "orange_wool", "white_bed", "red_bed", "yellow_bed", "glass", "glass_pane",
            "white_wool", "hay_block", "coal_block", "bookshelf");

        add(BlockDifficulty.HARD, Dimension.OVERWORLD, "rare-overworld", "diamond_block", "emerald_block",
            "enchanting_table", "ender_chest", "beacon", "conduit", "obsidian", "crying_obsidian",
            "amethyst_block", "sculk", "sculk_catalyst", "sculk_sensor", "sponge", "wet_sponge", "anvil",
            "blast_furnace", "sticky_piston", "copper_grate");
        add(BlockDifficulty.HARD, Dimension.NETHER, "nether", "netherrack", "soul_sand", "soul_soil", "magma_block",
            "basalt", "blackstone", "glowstone", "nether_bricks", "crimson_nylium", "warped_nylium",
            "crimson_stem", "warped_stem", "quartz_block", "ancient_debris", "copper_bulb");
        add(BlockDifficulty.HARD, Dimension.END, "end", "end_stone", "end_stone_bricks", "purpur_block",
            "purpur_pillar", "purpur_stairs", "purpur_slab", "end_rod");
    }

    private TargetCatalog() {}

    static List<Target> all() {
        if (resolved == null) {
            resolved = new ArrayList<>();
            for (Definition definition : DEFINITIONS) {
                Identifier id = Identifier.tryParse("minecraft:" + definition.id);
                if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
                    BlockShuffleMod.LOGGER.warn("Ignoring target absent from this Minecraft version: {}", definition.id);
                    continue;
                }
                Block block = BuiltInRegistries.BLOCK.getValue(id);
                Target target = new Target(block, id, definition.difficulty, definition.dimension,
                    definition.family, resourcesFor(definition.family));
                resolved.add(target);
                BY_BLOCK.put(block, target);
            }
            BlockShuffleMod.LOGGER.info("Loaded {} curated Block Shuffle targets", resolved.size());
        }
        return List.copyOf(resolved);
    }

    static Target forBlock(Block block) {
        all();
        return BY_BLOCK.get(block);
    }

    static List<Target> selectable(BlockDifficulty maximum, boolean enableNether, boolean enableEnd,
                                   Set<String> banned, Collection<Identifier> recentIds,
                                   Collection<String> recentResourceFamilies) {
        List<Target> eligible = all().stream()
            .filter(target -> maximum.allows(target.difficulty()))
            .filter(target -> target.dimension() != Dimension.NETHER || enableNether)
            .filter(target -> target.dimension() != Dimension.END || enableEnd)
            .filter(target -> !banned.contains(target.id().toString()))
            .toList();
        List<Target> cooled = eligible.stream()
            .filter(target -> !recentIds.contains(target.id()) && !recentResourceFamilies.contains(target.family()))
            .toList();
        return cooled.isEmpty() ? eligible : cooled;
    }

    private static void add(BlockDifficulty difficulty, Dimension dimension, String family, String... ids) {
        for (String id : ids) DEFINITIONS.add(new Definition(id, difficulty, dimension, family));
    }

    private static Set<String> resourcesFor(String family) {
        return switch (family) {
            case "oak" -> Set.of("oak_log", "oak_leaves");
            case "birch" -> Set.of("birch_log", "birch_leaves");
            case "spruce" -> Set.of("spruce_log", "spruce_leaves");
            case "jungle" -> Set.of("jungle_log", "jungle_leaves");
            case "acacia" -> Set.of("acacia_log", "acacia_leaves");
            case "dark_oak" -> Set.of("dark_oak_log", "dark_oak_leaves");
            case "cherry" -> Set.of("cherry_log", "cherry_leaves");
            case "mangrove" -> Set.of("mangrove_log", "mangrove_leaves", "mangrove_roots");
            case "bamboo" -> Set.of("bamboo", "bamboo_block");
            case "stone", "stone-craft" -> Set.of("stone", "cobblestone", "andesite", "diorite", "granite");
            case "craft-basic" -> Set.of("oak_log", "oak_leaves", "birch_log", "birch_leaves");
            case "geology" -> Set.of("stone", "granite", "diorite", "andesite", "tuff", "clay", "mud");
            case "iron" -> Set.of("iron_ore", "deepslate_iron_ore");
            case "copper" -> Set.of("copper_ore", "deepslate_copper_ore");
            case "nether" -> Set.of("netherrack", "blackstone", "basalt");
            case "end" -> Set.of("end_stone");
            default -> Set.of();
        };
    }

    private record Definition(String id, BlockDifficulty difficulty, Dimension dimension, String family) {}
}
