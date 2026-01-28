package mooshroomshark.msvanillaexpansion.world.gen;

import mooshroomshark.msvanillaexpansion.world.ModPlacedFeatures;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.gen.GenerationStep;

public class ModTreeGeneration {
    public static void generateTrees() {
        // Alternate oak tree variants in different forest types
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(BiomeKeys.FOREST),
                GenerationStep.Feature.VEGETAL_DECORATION, ModPlacedFeatures.ALTERNATE_OAK_PLACED_KEY);

        BiomeModifications.addFeature(BiomeSelectors.includeByKey(BiomeKeys.FLOWER_FOREST),
                GenerationStep.Feature.VEGETAL_DECORATION, ModPlacedFeatures.FLOWER_FOREST_OAK_PLACED_KEY);

        BiomeModifications.addFeature(BiomeSelectors.includeByKey(BiomeKeys.BIRCH_FOREST, BiomeKeys.OLD_GROWTH_BIRCH_FOREST),
                GenerationStep.Feature.VEGETAL_DECORATION, ModPlacedFeatures.BIRCH_FOREST_OAK_PLACED_KEY);

        // dead trees (oak logs without leaves) - very rare
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(
                BiomeKeys.PLAINS,
                BiomeKeys.SUNFLOWER_PLAINS,
                BiomeKeys.FOREST,
                BiomeKeys.FLOWER_FOREST,
                BiomeKeys.DARK_FOREST,
                BiomeKeys.SAVANNA,
                BiomeKeys.WINDSWEPT_SAVANNA
        ), GenerationStep.Feature.VEGETAL_DECORATION, ModPlacedFeatures.DEAD_TREE_PLACED_KEY);

        // Bedrock Edition giant red mushrooms in swamps
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(
                BiomeKeys.SWAMP,
                BiomeKeys.MANGROVE_SWAMP
        ), GenerationStep.Feature.VEGETAL_DECORATION, ModPlacedFeatures.SWAMP_GIANT_RED_MUSHROOM_PLACED_KEY);
    }
}
