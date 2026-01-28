package mooshroomshark.msvanillaexpansion.world;

import mooshroomshark.msvanillaexpansion.MooshroomSharksVanillaExpansion;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registerable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.intprovider.ConstantIntProvider;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.FeatureConfig;
import net.minecraft.world.gen.feature.TreeFeatureConfig;
import net.minecraft.world.gen.feature.HugeMushroomFeatureConfig;
import net.minecraft.world.gen.feature.size.TwoLayersFeatureSize;
import net.minecraft.world.gen.foliage.*;
import net.minecraft.world.gen.stateprovider.BlockStateProvider;
import net.minecraft.world.gen.trunk.DarkOakTrunkPlacer;
import net.minecraft.world.gen.trunk.ForkingTrunkPlacer;
import net.minecraft.world.gen.trunk.StraightTrunkPlacer;

public class ModConfiguredFeatures {
    //oak tree variants
    public static final RegistryKey<ConfiguredFeature<?, ?>> ALTERNATE_OAK_KEY = registerKey("alternateoak");
    public static final RegistryKey<ConfiguredFeature<?, ?>> FLOWER_FOREST_OAK_KEY = registerKey("flowerforestoak");
    public static final RegistryKey<ConfiguredFeature<?, ?>> BIRCH_FOREST_OAK_KEY = registerKey("birchforestoak");
    public static final RegistryKey<ConfiguredFeature<?, ?>> DEAD_TREE_KEY = registerKey("dead_tree");

    //Bedrock Edition features
    public static final RegistryKey<ConfiguredFeature<?, ?>> SWAMP_GIANT_RED_MUSHROOM_KEY = registerKey("swamp_giant_red_mushroom");

    public static void bootstrap(Registerable<ConfiguredFeature<?, ?>> context) {
        // variant oak tree configurations
        register(context, ALTERNATE_OAK_KEY, Feature.TREE, new TreeFeatureConfig.Builder(
                BlockStateProvider.of(Blocks.OAK_LOG),
                new StraightTrunkPlacer(5, 6, 3),
                BlockStateProvider.of(Blocks.OAK_LEAVES),
                new CherryFoliagePlacer(ConstantIntProvider.create(4), ConstantIntProvider.create(1), ConstantIntProvider.create(5),
                        0.25f, 0.5f, 0.15f, 0.05f),
                new TwoLayersFeatureSize(1, 0, 2)).build());

        register(context, FLOWER_FOREST_OAK_KEY, Feature.TREE, new TreeFeatureConfig.Builder(
                BlockStateProvider.of(Blocks.OAK_LOG),
                new ForkingTrunkPlacer(6, 7, 4),
                BlockStateProvider.of(Blocks.OAK_LEAVES),
                new BlobFoliagePlacer(ConstantIntProvider.create(2), ConstantIntProvider.create(1), 3),
                new TwoLayersFeatureSize(1, 0, 2)).build());

        register(context, BIRCH_FOREST_OAK_KEY, Feature.TREE, new TreeFeatureConfig.Builder(
                BlockStateProvider.of(Blocks.OAK_LOG),
                new DarkOakTrunkPlacer(9, 4, 3),
                BlockStateProvider.of(Blocks.OAK_LEAVES),
                new DarkOakFoliagePlacer(ConstantIntProvider.create(0), ConstantIntProvider.create(0)),
                new TwoLayersFeatureSize(2, 1, 4)).build());

        // dead tree configuration - oak logs without leaves
        register(context, DEAD_TREE_KEY, Feature.TREE, new TreeFeatureConfig.Builder(
                BlockStateProvider.of(Blocks.OAK_LOG),          // Trunk: Oak logs
                new StraightTrunkPlacer(4, 2, 0),               // Height: 4-6 blocks
                BlockStateProvider.of(Blocks.AIR),              // Foliage: None (air)
                new BlobFoliagePlacer(
                        ConstantIntProvider.create(0),              // No foliage radius
                        ConstantIntProvider.create(0),              // No foliage offset
                        0                                           // No foliage height
                ),
                new TwoLayersFeatureSize(1, 0, 1)              // Feature size
        ).ignoreVines().build());                          // Don't add vines

        // giant red mushroom for swamps (Bedrock parity)
        register(context, SWAMP_GIANT_RED_MUSHROOM_KEY, Feature.HUGE_RED_MUSHROOM,
                new HugeMushroomFeatureConfig(
                        BlockStateProvider.of(Blocks.RED_MUSHROOM_BLOCK.getDefaultState()),  // Cap
                        BlockStateProvider.of(Blocks.MUSHROOM_STEM.getDefaultState()),       // Stem
                        2                                                                      // Cap radius
                ));
    }

    public static RegistryKey<ConfiguredFeature<?, ?>> registerKey(String name) {
        return RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, Identifier.of(MooshroomSharksVanillaExpansion.MOD_ID, name));
    }

    private static <FC extends FeatureConfig, F extends Feature<FC>> void register(Registerable<ConfiguredFeature<?, ?>> context,
                                                                                   RegistryKey<ConfiguredFeature<?, ?>> key, F feature, FC configuration) {
        context.register(key, new ConfiguredFeature<>(feature, configuration));
    }
}
