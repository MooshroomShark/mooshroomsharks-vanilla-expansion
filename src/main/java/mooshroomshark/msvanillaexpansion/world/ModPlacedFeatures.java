package mooshroomshark.msvanillaexpansion.world;

import mooshroomshark.msvanillaexpansion.MooshroomSharksVanillaExpansion;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registerable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.feature.*;
import net.minecraft.world.gen.placementmodifier.*;

import java.util.List;

public class ModPlacedFeatures {
    // variant oak tree features
    public static final RegistryKey<PlacedFeature> ALTERNATE_OAK_PLACED_KEY = registerKey("alternateoak_placed");
    public static final RegistryKey<PlacedFeature> FLOWER_FOREST_OAK_PLACED_KEY = registerKey("flowerforestoak_placed");
    public static final RegistryKey<PlacedFeature> BIRCH_FOREST_OAK_PLACED_KEY = registerKey("birchforestoak_placed");
    public static final RegistryKey<PlacedFeature> DEAD_TREE_PLACED_KEY = registerKey("dead_tree_placed");

    // Bedrock Edition features
    public static final RegistryKey<PlacedFeature> DYING_OAK_PLACED_KEY = registerKey("dyingoak_placed");
    public static final RegistryKey<PlacedFeature> SWAMP_GIANT_RED_MUSHROOM_PLACED_KEY = registerKey("swamp_giant_red_mushroom_placed");

    public static void bootstrap(Registerable<PlacedFeature> context) {
        var configuredFeatureRegistryEntryLookup = context.getRegistryLookup(RegistryKeys.CONFIGURED_FEATURE);

        // variant oak tree placements
        register(context, ALTERNATE_OAK_PLACED_KEY, configuredFeatureRegistryEntryLookup.getOrThrow(ModConfiguredFeatures.ALTERNATE_OAK_KEY),
                VegetationPlacedFeatures.treeModifiersWithWouldSurvive(
                        PlacedFeatures.createCountExtraModifier(2, 0.1f, 2), Blocks.OAK_SAPLING));

        register(context, FLOWER_FOREST_OAK_PLACED_KEY, configuredFeatureRegistryEntryLookup.getOrThrow(ModConfiguredFeatures.FLOWER_FOREST_OAK_KEY),
                VegetationPlacedFeatures.treeModifiersWithWouldSurvive(
                        PlacedFeatures.createCountExtraModifier(1, 0.1f, 1), Blocks.OAK_SAPLING));

        register(context, BIRCH_FOREST_OAK_PLACED_KEY, configuredFeatureRegistryEntryLookup.getOrThrow(ModConfiguredFeatures.BIRCH_FOREST_OAK_KEY),
                VegetationPlacedFeatures.treeModifiersWithWouldSurvive(
                        PlacedFeatures.createCountExtraModifier(1, 0.01f, 3), Blocks.OAK_SAPLING));

        // dead tree placement - very rare (1 in 500 chunks)
        register(context, DEAD_TREE_PLACED_KEY,
                configuredFeatureRegistryEntryLookup.getOrThrow(ModConfiguredFeatures.DEAD_TREE_KEY),
                RarityFilterPlacementModifier.of(500),          // 1 in 500 chunks
                SquarePlacementModifier.of(),                   // Random XZ within chunk
                PlacedFeatures.MOTION_BLOCKING_HEIGHTMAP,       // Place on ground surface
                BiomePlacementModifier.of()                     // Only in registered biomes
        );

        // giant red mushroom placement - moderately rare (1 in 50 chunks) to match Bedrock Edition
        register(context, SWAMP_GIANT_RED_MUSHROOM_PLACED_KEY,
                configuredFeatureRegistryEntryLookup.getOrThrow(ModConfiguredFeatures.SWAMP_GIANT_RED_MUSHROOM_KEY),
                CountPlacementModifier.of(1),                   // 1 attempt per chunk
                RarityFilterPlacementModifier.of(50),           // 1 in 50 chunks
                SquarePlacementModifier.of(),                   // Random XZ within chunk
                PlacedFeatures.MOTION_BLOCKING_HEIGHTMAP,       // Place on ground surface
                BiomePlacementModifier.of()                     // Only in registered biomes
        );

        //Dying Tree Placemnt (similar to bedrock)
        register(context, DYING_OAK_PLACED_KEY, configuredFeatureRegistryEntryLookup.getOrThrow(ModConfiguredFeatures.DYING_OAK_KEY),
                VegetationPlacedFeatures.treeModifiersWithWouldSurvive(
                        PlacedFeatures.createCountExtraModifier(0, 0.1f, 1), Blocks.OAK_SAPLING));
    }

    public static RegistryKey<PlacedFeature> registerKey(String name) {
        return RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(MooshroomSharksVanillaExpansion.MOD_ID, name));
    }

    private static void register(Registerable<PlacedFeature> context, RegistryKey<PlacedFeature> key, RegistryEntry<ConfiguredFeature<?, ?>> configuration,
                                 List<PlacementModifier> modifiers) {
        context.register(key, new PlacedFeature(configuration, List.copyOf(modifiers)));
    }

    private static <FC extends FeatureConfig, F extends Feature<FC>> void register(Registerable<PlacedFeature> context, RegistryKey<PlacedFeature> key,
                                                                                   RegistryEntry<ConfiguredFeature<?, ?>> configuration,
                                                                                   PlacementModifier... modifiers) {
        register(context, key, configuration, List.of(modifiers));
    }
}
