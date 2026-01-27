package mooshroomshark.msvanillaexpansion;

import mooshroomshark.msvanillaexpansion.entities.PetProtectionHandler;
import mooshroomshark.msvanillaexpansion.entities.PetChunkLoadingHandler;
import mooshroomshark.msvanillaexpansion.world.gen.ModWorldGeneration;
import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MooshroomSharksVanillaExpansion implements ModInitializer {
	public static final String MOD_ID = "msvanillaexpansion";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
        LOGGER.info("Initializing MooshroomShark's Vanilla Expansion");

        // Register pet protection (prevents owners from harming pets unless sneaking)
        PetProtectionHandler.register();

        // Register pet chunk loading (keeps chunks loaded for non-sitting pets)
        PetChunkLoadingHandler.register();

        // Register world generation (dead trees and swamp mushrooms)
        ModWorldGeneration.generateModWorldGeneration();

        LOGGER.info("MooshroomShark's Vanilla Expansion initialized successfully!");
	}
}