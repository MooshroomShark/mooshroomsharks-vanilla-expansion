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

import java.util.HashMap;
import java.util.Map;

/**
 * Handles chunk loading for non-sitting tamed pets to ensure they can teleport to their owner.
 * This prevents pets from getting stuck in unloaded chunks when the player travels far away.
 */
public class PetChunkLoadingHandler {

    // Track chunks that need to stay loaded for pets
    private static final Map<ChunkPos, Integer> petChunkTickets = new HashMap<>();

    // Track failed teleport attempts to prevent log spam
    private static final Map<Integer, Long> failedTeleportCooldowns = new HashMap<>();
    private static final long TELEPORT_RETRY_COOLDOWN = 100; // 5 seconds (100 ticks)

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

        int petsFound = 0;
        int petsLoadedChunks = 0;
        int petsTeleported = 0;

        // Find all tamed pets that are not sitting and their owner is not nearby
        for (Entity entity : world.iterateEntities()) {
            if (entity instanceof TameableEntity tameable) {
                // Only process wolves, cats, and parrots
                if (entity instanceof WolfEntity || entity instanceof CatEntity || entity instanceof ParrotEntity) {
                    petsFound++;

                    // Check if pet is tamed, not sitting, and has an owner
                    if (tameable.isTamed() && !tameable.isSitting() && tameable.getOwner() != null) {
                        double distanceSquared = tameable.squaredDistanceTo(tameable.getOwner());
                        double distance = Math.sqrt(distanceSquared);

                        // Check if owner is in a different dimension or far away (>12 blocks)
                        if (tameable.getOwner().getEntityWorld() != world ||
                                distanceSquared > 144.0) {

                            // Get the chunk position of the pet
                            ChunkPos chunkPos = new ChunkPos(entity.getBlockPos());

                            // Track this chunk (count multiple pets in same chunk)
                            petChunkTickets.put(chunkPos, petChunkTickets.getOrDefault(chunkPos, 0) + 1);

                            // Add chunk ticket to keep it loaded
                            // Level 2 means the chunk and adjacent chunks stay loaded
                            world.getChunkManager().addTicket(PET_TICKET, chunkPos, 2);

                            petsLoadedChunks++;

                            // If pet is beyond vanilla teleport range (~192 blocks), manually teleport it
                            if (distanceSquared > 36864.0) { // 192 blocks squared
                                // Check if we're on cooldown for this pet
                                int petId = entity.getId();
                                long currentTick = world.getTime();
                                Long lastFailedAttempt = failedTeleportCooldowns.get(petId);

                                // Only attempt teleport if not on cooldown or cooldown expired
                                if (lastFailedAttempt == null || (currentTick - lastFailedAttempt) >= TELEPORT_RETRY_COOLDOWN) {
                                    // Find a safe location near the owner
                                    double targetX = tameable.getOwner().getX();
                                    double targetY = tameable.getOwner().getY();
                                    double targetZ = tameable.getOwner().getZ();

                                    // Try to find a safe spot within 3 blocks of the owner
                                    net.minecraft.util.math.BlockPos safePos = findSafeTeleportLocation(world, targetX, targetY, targetZ);

                                    if (safePos != null) {
                                        // Teleport pet to safe position
                                        entity.teleport(
                                                world,
                                                safePos.getX() + 0.5,
                                                safePos.getY(),
                                                safePos.getZ() + 0.5,
                                                java.util.Set.of(),
                                                entity.getYaw(),
                                                entity.getPitch(),
                                                false
                                        );
                                        petsTeleported++;

                                        // Remove from cooldown on success
                                        failedTeleportCooldowns.remove(petId);

                                        MooshroomSharksVanillaExpansion.LOGGER.info(
                                                "Force teleported {} from {} blocks away to owner at safe location {}",
                                                entity.getType().getName().getString(),
                                                (int) distance,
                                                safePos
                                        );
                                    } else {
                                        // Put on cooldown and log warning
                                        failedTeleportCooldowns.put(petId, currentTick);

                                        MooshroomSharksVanillaExpansion.LOGGER.warn(
                                                "Could not find safe teleport location for {} near owner at ({}, {}, {}). Will retry in 5 seconds.",
                                                entity.getType().getName().getString(),
                                                (int) targetX, (int) targetY, (int) targetZ
                                        );
                                    }
                                }
                            } else {
                                MooshroomSharksVanillaExpansion.LOGGER.info(
                                        "Loading chunk for {} at {} (distance from owner: {})",
                                        entity.getType().getName().getString(),
                                        chunkPos,
                                        (int) distance
                                );
                            }
                        }
                    }
                }
            }
        }

        if (petsLoadedChunks > 0) {
            MooshroomSharksVanillaExpansion.LOGGER.info(
                    "Keeping {} chunks loaded for {} pets (found {} total pets, force teleported {})",
                    petChunkTickets.size(), petsLoadedChunks, petsFound, petsTeleported
            );
        }
    }

    /**
     * Finds a safe location to teleport a pet near the target coordinates.
     * Checks for solid ground, no suffocation, no lava/fire, and reasonable fall distance.
     *
     * @param world The world to search in
     * @param x Target X coordinate
     * @param y Target Y coordinate
     * @param z Target Z coordinate
     * @return A safe BlockPos, or null if no safe location found
     */
    private static net.minecraft.util.math.BlockPos findSafeTeleportLocation(ServerWorld world, double x, double y, double z) {
        net.minecraft.util.math.BlockPos targetPos = net.minecraft.util.math.BlockPos.ofFloored(x, y, z);

        // First, try the exact owner position
        if (isSafeTeleportLocation(world, targetPos)) {
            return targetPos;
        }

        // Search in a 5x5x5 area around the owner
        for (int xOffset = -2; xOffset <= 2; xOffset++) {
            for (int zOffset = -2; zOffset <= 2; zOffset++) {
                for (int yOffset = -2; yOffset <= 2; yOffset++) {
                    net.minecraft.util.math.BlockPos checkPos = targetPos.add(xOffset, yOffset, zOffset);

                    if (isSafeTeleportLocation(world, checkPos)) {
                        return checkPos;
                    }
                }
            }
        }

        // If still no safe spot, try to find solid ground below the owner (up to 10 blocks down)
        for (int i = 0; i < 10; i++) {
            net.minecraft.util.math.BlockPos downPos = targetPos.down(i);
            if (isSafeTeleportLocation(world, downPos)) {
                return downPos;
            }
        }

        // No safe location found
        return null;
    }

    /**
     * Checks if a location is safe for a pet to teleport to.
     *
     * @param world The world
     * @param pos The position to check
     * @return true if safe, false otherwise
     */
    private static boolean isSafeTeleportLocation(ServerWorld world, net.minecraft.util.math.BlockPos pos) {
        // Check the block at the position (where pet's feet will be)
        net.minecraft.block.BlockState groundState = world.getBlockState(pos);

        // Check the block above (where pet's body will be)
        net.minecraft.block.BlockState bodyState = world.getBlockState(pos.up());

        // Check the block 2 above (for tall pets like wolves)
        net.minecraft.block.BlockState headState = world.getBlockState(pos.up(2));

        // The ground must be solid (not air, not liquid)
        if (!groundState.isSolidBlock(world, pos)) {
            return false;
        }

        // The body space must be passable (air or non-solid)
        if (bodyState.isSolidBlock(world, pos.up())) {
            return false; // Would suffocate
        }

        // The head space should be passable
        if (headState.isSolidBlock(world, pos.up(2))) {
            return false; // Would suffocate
        }

        // Check for dangerous blocks
        if (isDangerousBlock(groundState) || isDangerousBlock(bodyState) || isDangerousBlock(headState)) {
            return false;
        }

        // Check that there's not a huge fall below (pets take fall damage)
        int fallDistance = 0;
        for (int i = 1; i <= 10; i++) {
            net.minecraft.block.BlockState belowState = world.getBlockState(pos.down(i));
            if (belowState.isSolidBlock(world, pos.down(i))) {
                break;
            }
            fallDistance++;
        }

        // If fall distance is more than 3 blocks, it's not safe
        if (fallDistance > 3) {
            return false;
        }

        return true;
    }

    /**
     * Checks if a block is dangerous for pets (lava, fire, cactus, etc.)
     */
    private static boolean isDangerousBlock(net.minecraft.block.BlockState state) {
        net.minecraft.block.Block block = state.getBlock();

        return block instanceof net.minecraft.block.AbstractFireBlock ||
                block instanceof net.minecraft.block.CampfireBlock ||
                block instanceof net.minecraft.block.MagmaBlock ||
                block instanceof net.minecraft.block.CactusBlock ||
                block instanceof net.minecraft.block.SweetBerryBushBlock ||
                block instanceof net.minecraft.block.WitherRoseBlock ||
                state.getBlock() == net.minecraft.block.Blocks.LAVA ||
                state.getBlock() == net.minecraft.block.Blocks.POWDER_SNOW;
    }
}