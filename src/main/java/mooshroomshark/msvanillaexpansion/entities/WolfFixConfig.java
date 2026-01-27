package mooshroomshark.msvanillaexpansion.entities;

import mooshroomshark.msvanillaexpansion.MooshroomSharksVanillaExpansion;

public class WolfFixConfig {
    // Teleportation settings
    public static boolean IMPROVED_TELEPORTATION = true;
    public static int MAX_TELEPORT_RANGE = 64;

    // Follow settings
    public static boolean BETTER_FOLLOWING = true;
    public static double FOLLOW_DISTANCE_MIN = 3.0;
    public static double FOLLOW_DISTANCE_MAX = 10.0;

    public static void init() {
        MooshroomSharksVanillaExpansion.LOGGER.info("Wolf Fix Configuration loaded");
        MooshroomSharksVanillaExpansion.LOGGER.info("- Improved Teleportation: {}", IMPROVED_TELEPORTATION);
        MooshroomSharksVanillaExpansion.LOGGER.info("- Max Teleport Range: {} blocks", MAX_TELEPORT_RANGE);
        MooshroomSharksVanillaExpansion.LOGGER.info("- Better Following: {}", BETTER_FOLLOWING);
        MooshroomSharksVanillaExpansion.LOGGER.info("- Follow Distance: {}-{} blocks", FOLLOW_DISTANCE_MIN, FOLLOW_DISTANCE_MAX);
    }
}
