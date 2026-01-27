package mooshroomshark.msvanillaexpansion.entities;

import mooshroomshark.msvanillaexpansion.MooshroomSharksVanillaExpansion;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles chunk loading for non-sitting tamed pets to ensure they can teleport to their owner.
 * This prevents pets from getting stuck in unloaded chunks when the player travels far away.
 */
public class PetChunkLoadingHandler {

    // Track chunks that need to stay loaded for pets
    private static final Map<ChunkPos, Integer> petChunkTickets = new HashMap<>();

    // Custom chunk ticket type with 100 tick (5 second) lifetime
    private static final ChunkTicketType PET_TICKET =
            new ChunkTicketType(100L, 100);

    public static void register() {
        // Register server tick event to manage pet chunk loading
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerWorld world : server.getWorlds()) {
                handlePetChunkLoading(world);
            }
        });
        MooshroomSharksVanillaExpansion.LOGGER.info("Pet chunk loading registered");
    }

    private static void handlePetChunkLoading(ServerWorld world) {
        // Clear old tickets from previous tick
        petChunkTickets.clear();

        // Find all tamed pets that are not sitting and their owner is not nearby
        for (Entity entity : world.iterateEntities()) {
            if (entity instanceof TameableEntity tameable) {
                // Only process wolves, cats, and parrots
                if (entity instanceof WolfEntity || entity instanceof CatEntity || entity instanceof ParrotEntity) {
                    // Check if pet is tamed, not sitting, and has an owner
                    if (tameable.isTamed() && !tameable.isSitting() && tameable.getOwner() != null) {
                        // Check if owner is in a different dimension or far away (>12 blocks)
                        if (tameable.getOwner().getEntityWorld() != world ||
                                tameable.squaredDistanceTo(tameable.getOwner()) > 144.0) {

                            // Get the chunk position of the pet
                            ChunkPos chunkPos = new ChunkPos(entity.getBlockPos());

                            // Track this chunk (count multiple pets in same chunk)
                            petChunkTickets.put(chunkPos, petChunkTickets.getOrDefault(chunkPos, 0) + 1);

                            // Add chunk ticket to keep it loaded
                            // Level 2 means the chunk and adjacent chunks stay loaded
                            world.getChunkManager().addTicket(PET_TICKET, chunkPos, 2);
                        }
                    }
                }
            }
        }
    }
}
