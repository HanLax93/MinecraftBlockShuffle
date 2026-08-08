package me.matistan05.minecraftblockshuffle.fabric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;

import java.util.Set;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockShuffleRulesTest {
    private static final Set<String> FORBIDDEN = Set.of(
        "minecraft:test_instance_block", "minecraft:petrified_oak_slab", "minecraft:moving_piston",
        "minecraft:vault", "minecraft:water_cauldron", "minecraft:pearlescent_froglight"
    );

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void normalDifficultyProgressesFromEasyToHard() {
        assertEquals(BlockDifficulty.EASY, BlockShuffleGame.difficultyForRound(BlockDifficulty.NORMAL, 1));
        assertEquals(BlockDifficulty.EASY, BlockShuffleGame.difficultyForRound(BlockDifficulty.NORMAL, 3));
        assertEquals(BlockDifficulty.NORMAL, BlockShuffleGame.difficultyForRound(BlockDifficulty.NORMAL, 4));
        assertEquals(BlockDifficulty.NORMAL, BlockShuffleGame.difficultyForRound(BlockDifficulty.NORMAL, 7));
        assertEquals(BlockDifficulty.HARD, BlockShuffleGame.difficultyForRound(BlockDifficulty.NORMAL, 8));
    }

    @Test
    void easyModeNeverEscalates() {
        assertEquals(BlockDifficulty.EASY, BlockShuffleGame.difficultyForRound(BlockDifficulty.EASY, 100));
    }

    @Test
    void catalogExcludesKnownImpossibleAndStateOnlyTargets() {
        Set<String> ids = TargetCatalog.all().stream().map(target -> target.id().toString()).collect(Collectors.toSet());
        assertTrue(ids.contains("minecraft:grass_block"));
        assertFalse(ids.stream().anyMatch(FORBIDDEN::contains));
        assertEquals(ids.size(), TargetCatalog.all().size(), "The curated target catalog must not contain duplicates");
    }

    @Test
    void everyTargetHasAnItemAndCollisionSurface() {
        for (TargetCatalog.Target target : TargetCatalog.all()) {
            assertFalse(target.block().asItem() == Items.AIR, () -> target.id() + " has no obtainable item form");
            assertFalse(target.block().defaultBlockState()
                .getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty(),
                () -> target.id() + " has no surface a player can stand on");
        }
    }

    @Test
    void easyCatalogIsOverworldOnly() {
        assertTrue(TargetCatalog.all().stream()
            .filter(target -> target.difficulty() == BlockDifficulty.EASY)
            .allMatch(target -> target.dimension() == TargetCatalog.Dimension.OVERWORLD));
        assertTrue(TargetCatalog.all().stream()
            .filter(target -> target.difficulty() == BlockDifficulty.EASY)
            .allMatch(target -> !target.nearbyResources().isEmpty()),
            "Every easy target must have a common resource signal for teleport scoring");
    }

    @Test
    void selectionHonorsDifficultyDimensionBanAndFamilyCooldown() {
        var easy = TargetCatalog.selectable(BlockDifficulty.EASY, false, false, Set.of(), List.of(), List.of());
        assertTrue(easy.stream().allMatch(target -> target.difficulty() == BlockDifficulty.EASY));
        assertTrue(easy.stream().allMatch(target -> target.dimension() == TargetCatalog.Dimension.OVERWORLD));

        var hardWithoutDimensions = TargetCatalog.selectable(BlockDifficulty.HARD, false, false, Set.of(), List.of(), List.of());
        assertFalse(hardWithoutDimensions.stream().anyMatch(target -> target.dimension() != TargetCatalog.Dimension.OVERWORLD));

        var filtered = TargetCatalog.selectable(BlockDifficulty.EASY, false, false,
            Set.of("minecraft:grass_block"), List.of(), List.of("oak"));
        assertFalse(filtered.stream().anyMatch(target -> target.id().toString().equals("minecraft:grass_block")));
        assertFalse(filtered.stream().anyMatch(target -> target.family().equals("oak")));
    }

    @Test
    void skipRequiresARealMajority() {
        assertEquals(1, BlockShuffleGame.requiredSkipVotes(1));
        assertEquals(2, BlockShuffleGame.requiredSkipVotes(2));
        assertEquals(2, BlockShuffleGame.requiredSkipVotes(3));
        assertEquals(3, BlockShuffleGame.requiredSkipVotes(4));
    }
}
