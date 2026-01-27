package mooshroomshark.msvanillaexpansion;

import mooshroomshark.msvanillaexpansion.entities.WolfFixConfig;
import mooshroomshark.msvanillaexpansion.world.gen.ModWorldGeneration;
import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MooshroomSharksVanillaExpansion implements ModInitializer {
	public static final String MOD_ID = "msvanillaexpansion";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
        // Register configuration
        WolfFixConfig.init();
        // Register world generation
        ModWorldGeneration.generateModWorldGeneration();
	}
}