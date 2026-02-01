package mooshroomshark.msvanillaexpansion.entities;

import mooshroomshark.msvanillaexpansion.MooshroomSharksVanillaExpansion;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.vehicle.ChestBoatEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.*;

public class PetChunkLoadingHandler {

    private static final Map<ChunkPos, Integer> petChunkTickets = new HashMap<>();
    private static final Map<Integer, Long> failedTeleportCooldowns = new HashMap<>();
    private static final long TELEPORT_RETRY_COOLDOWN = 100; // 5 seconds (100 ticks)

    // Increased teleport distance thresholds
    private static final double TELEPORT_DISTANCE_SQUARED = 4096.0; // 64 blocks squared

    // Boat boarding settings
    private static final double BOAT_BOARDING_DISTANCE_SQUARED = 100.0; // 10 blocks squared
    private static final Map<Integer, Integer> petLastBoatId = new HashMap<>(); // Track which boat pet was in

    // Use the same approach as in your original working code
    private static final ChunkTicketType PET_TICKET = new ChunkTicketType(100L, 100);

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerWorld world : server.getWorlds()) {
                handlePetChunkLoading(world);
            }
        });
        MooshroomSharksVanillaExpansion.LOGGER.info("Pet chunk loading registered");
    }

    private static void handlePetChunkLoading(ServerWorld world) {
        // Track which chunks we're keeping loaded this tick
        Map<ChunkPos, Integer> currentTickTickets = new HashMap<>();

        int petsFound = 0;
        int petsLoadedChunks = 0;
        int petsTeleported = 0;
        int petsBoarded = 0;

        // Collect pets first to avoid modifying collection while iterating
        List<Entity> petsToProcess = new ArrayList<>();
        for (Entity entity : world.iterateEntities()) {
            if (entity instanceof TameableEntity tameable) {
                if (entity instanceof WolfEntity || entity instanceof CatEntity || entity instanceof ParrotEntity) {
                    if (tameable.isTamed() && !tameable.isSitting() && tameable.getOwner() != null) {
                        petsToProcess.add(entity);
                    }
                }
            }
        }

        // Now process the collected pets
        for (Entity entity : petsToProcess) {
            TameableEntity tameable = (TameableEntity) entity;
            petsFound++;

            Entity owner = tameable.getOwner();
            double distanceSquared = tameable.squaredDistanceTo(owner);
            double distance = Math.sqrt(distanceSquared);

            int petId = entity.getId();
            long currentTick = world.getTime();
            Long lastFailedAttempt = failedTeleportCooldowns.get(petId);
            boolean hasPendingRetry = lastFailedAttempt != null;

            // Always load chunks for both pet and owner locations
            ChunkPos petChunkPos = new ChunkPos(entity.getBlockPos());
            ChunkPos ownerChunkPos = new ChunkPos(owner.getBlockPos());

            // Load both chunks to ensure smooth teleportation
            for (ChunkPos chunkPos : new ChunkPos[]{petChunkPos, ownerChunkPos}) {
                int ticketCount = currentTickTickets.getOrDefault(chunkPos, 0) + 1;
                currentTickTickets.put(chunkPos, ticketCount);

                // Only add ticket if we haven't already for this chunk this tick
                if (ticketCount == 1) {
                    // Use the correct addTicket method for 1.21.11
                    world.getChunkManager().addTicket(PET_TICKET, chunkPos, 3); // Higher ticket level
                }
                petsLoadedChunks++;
            }

            // NEW: Check if owner is in a boat and pet should board
            if (handleBoatBoarding(world, entity, tameable, owner, distanceSquared)) {
                petsBoarded++;
                continue; // Skip normal teleportation if pet boarded a boat
            }

            // NEW: Check if pet should exit boat when owner exits
            handleBoatExiting(entity, owner, petId);

            // Try to teleport if very far (more than 64 blocks) or has pending retry
            if (distanceSquared > TELEPORT_DISTANCE_SQUARED || hasPendingRetry) {
                if (lastFailedAttempt == null || (currentTick - lastFailedAttempt) >= TELEPORT_RETRY_COOLDOWN) {

                    double targetX = owner.getX();
                    double targetY = owner.getY();
                    double targetZ = owner.getZ();

                    // Check if owner is in water
                    boolean ownerInWater = owner.isTouchingWater() ||
                            world.getFluidState(owner.getBlockPos()).isOf(Fluids.WATER);

                    BlockPos safePos = findSafeTeleportLocation(world, targetX, targetY, targetZ, ownerInWater);

                    if (safePos != null) {
                        // Remove from failed attempts
                        failedTeleportCooldowns.remove(petId);

                        // Teleport the pet
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

                        MooshroomSharksVanillaExpansion.LOGGER.info(
                                "Teleported {} from {} blocks away to owner at safe location {}",
                                entity.getType().getName().getString(),
                                (int) distance,
                                safePos
                        );
                    } else {
                        // Record failed attempt with current time
                        failedTeleportCooldowns.put(petId, currentTick);

                        // Add debug logging
                        BlockPos targetPos = BlockPos.ofFloored(targetX, targetY, targetZ);
                        MooshroomSharksVanillaExpansion.LOGGER.warn(
                                "Could not find safe teleport location for {} near owner at ({}, {}, {}). " +
                                        "Owner in water: {}. Ground solid: {}. Body clear: {}. Will retry in {} seconds.",
                                entity.getType().getName().getString(),
                                (int) targetX, (int) targetY, (int) targetZ,
                                ownerInWater,
                                world.getBlockState(targetPos.down()).isSolidBlock(world, targetPos.down()),
                                world.getBlockState(targetPos).isAir(),
                                TELEPORT_RETRY_COOLDOWN / 20
                        );
                    }
                } else {
                    // Still on cooldown
                    if ((currentTick - lastFailedAttempt) % 20 == 0) {
                        long ticksRemaining = TELEPORT_RETRY_COOLDOWN - (currentTick - lastFailedAttempt);
                        long secondsRemaining = ticksRemaining / 20;
                        MooshroomSharksVanillaExpansion.LOGGER.debug(
                                "{} at {} blocks away, waiting {} seconds before retry teleport",
                                entity.getType().getName().getString(),
                                (int) distance,
                                secondsRemaining
                        );
                    }
                }
            }
        }

        // Update the global map with current tick's tickets
        petChunkTickets.clear();
        petChunkTickets.putAll(currentTickTickets);

        // Log status every 5 seconds (debug only)
        if (petsLoadedChunks > 0 && world.getTime() % 100 == 0) {
            MooshroomSharksVanillaExpansion.LOGGER.debug(
                    "Keeping {} chunks loaded for {} pets (found {} total pets, teleported {} this tick, boarded {} boats)",
                    petChunkTickets.size(), petsLoadedChunks, petsFound, petsTeleported, petsBoarded
            );
        }
    }

    /**
     * Handles automatic boat boarding for pets when their owner gets in a boat.
     * Pets will teleport into the back of the boat if they're close enough.
     *
     * @return true if the pet boarded a boat, false otherwise
     */
    private static boolean handleBoatBoarding(ServerWorld world, Entity petEntity, TameableEntity tameable,
                                              Entity owner, double distanceSquared) {
        int petId = petEntity.getId();

        // Check if owner is in a boat
        Entity ownerVehicle = owner.getVehicle();
        if (!(ownerVehicle instanceof BoatEntity)) {
            // Owner not in boat - clear tracking
            petLastBoatId.remove(petId);
            return false;
        }

        BoatEntity boat = (BoatEntity) ownerVehicle;
        int boatId = boat.getId();

        // Check if pet is already in this boat
        if (petEntity.getVehicle() == boat) {
            petLastBoatId.put(petId, boatId);
            return true; // Already in boat
        }

        // Check if we've already processed this boat for this pet
        Integer lastBoat = petLastBoatId.get(petId);
        if (lastBoat != null && lastBoat == boatId) {
            // We already tried to board this boat, don't spam attempts
            return false;
        }

        // Check if pet is close enough to board (within 10 blocks)
        if (distanceSquared > BOAT_BOARDING_DISTANCE_SQUARED) {
            return false;
        }

        // Check if boat has room
        // Regular boats have 2 passenger slots (indices 0 and 1)
        // Chest boats only have 1 passenger slot (index 0) since the chest takes up the back
        boolean isChestBoat = ownerVehicle instanceof ChestBoatEntity;
        int maxPassengers = isChestBoat ? 1 : 2;

        List<Entity> passengers = boat.getPassengerList();
        if (passengers.size() >= maxPassengers) {
            // Boat is full
            MooshroomSharksVanillaExpansion.LOGGER.debug(
                    "Cannot board {} to boat - boat is full ({}/{})",
                    petEntity.getType().getName().getString(),
                    passengers.size(),
                    maxPassengers
            );
            return false;
        }

        // Board the pet!
        boolean success = petEntity.startRiding(boat);

        if (success) {
            petLastBoatId.put(petId, boatId);
            MooshroomSharksVanillaExpansion.LOGGER.info(
                    "Boarded {} into owner's boat (type: {})",
                    petEntity.getType().getName().getString(),
                    isChestBoat ? "chest boat" : "regular boat"
            );
        } else {
            MooshroomSharksVanillaExpansion.LOGGER.warn(
                    "Failed to board {} into boat",
                    petEntity.getType().getName().getString()
            );
        }

        return success;
    }

    /**
     * Handles automatic boat exiting for pets when their owner exits a boat.
     * Pets will dismount from boats when their owner is no longer in a boat.
     */
    private static void handleBoatExiting(Entity petEntity, Entity owner, int petId) {
        Entity petVehicle = petEntity.getVehicle();

        // Check if pet is in a boat
        if (!(petVehicle instanceof BoatEntity)) {
            return; // Pet not in a boat, nothing to do
        }

        // Check if owner is still in a boat
        Entity ownerVehicle = owner.getVehicle();
        if (ownerVehicle instanceof BoatEntity) {
            return; // Owner still in boat, pet should stay
        }

        // Owner has exited the boat, so pet should exit too
        petEntity.stopRiding();
        petLastBoatId.remove(petId); // Clear tracking

        MooshroomSharksVanillaExpansion.LOGGER.info(
                "{} exited boat because owner left",
                petEntity.getType().getName().getString()
        );
    }

    private static BlockPos findSafeTeleportLocation(ServerWorld world, double x, double y, double z, boolean ownerInWater) {
        BlockPos targetPos = BlockPos.ofFloored(x, y, z);

        // STRATEGY 1: Try the exact position first
        if (isSafeTeleportLocation(world, targetPos, ownerInWater)) {
            return targetPos;
        }

        // STRATEGY 2: Try finding surface position
        BlockPos surfacePos = findSurfacePosition(world, targetPos);
        if (surfacePos != null && isSafeTeleportLocation(world, surfacePos, ownerInWater)) {
            return surfacePos;
        }

        // STRATEGY 3: Try nearby positions in a small radius
        for (int radius = 1; radius <= 3; radius++) {
            for (int xOffset = -radius; xOffset <= radius; xOffset++) {
                for (int zOffset = -radius; zOffset <= radius; zOffset++) {
                    BlockPos checkPos = findSurfacePosition(world, targetPos.add(xOffset, 0, zOffset));
                    if (checkPos != null && isSafeTeleportLocation(world, checkPos, ownerInWater)) {
                        return checkPos;
                    }
                }
            }
        }

        // STRATEGY 4: Try positions above player (for caves/underground)
        for (int yOffset = 1; yOffset <= 10; yOffset++) {
            BlockPos checkPos = targetPos.add(0, yOffset, 0);
            if (isSafeTeleportLocation(world, checkPos, ownerInWater)) {
                return checkPos;
            }
        }

        // STRATEGY 5: Last resort - any safe position in a larger area
        for (int radius = 1; radius <= 12; radius++) {
            for (int xOffset = -radius; xOffset <= radius; xOffset++) {
                for (int yOffset = -5; yOffset <= 10; yOffset++) {
                    for (int zOffset = -radius; zOffset <= radius; zOffset++) {
                        BlockPos checkPos = targetPos.add(xOffset, yOffset, zOffset);
                        if (isSafeTeleportLocation(world, checkPos, ownerInWater)) {
                            return checkPos;
                        }
                    }
                }
            }
        }

        return null;
    }

    private static BlockPos findSurfacePosition(ServerWorld world, BlockPos pos) {
        // Start from current position and move up/down to find surface
        BlockPos.Mutable mutable = pos.mutableCopy();

        // Get world height limits for 1.21.11
        int bottomY = -64; // Minecraft 1.21.11 minimum height
        int topY = 320;    // Minecraft 1.21.11 maximum height

        // First, if we're in solid blocks, move up to find air
        BlockState currentState = world.getBlockState(mutable);
        int attempts = 0;

        // Try moving up to find air (max 20 blocks up)
        while ((currentState.isSolidBlock(world, mutable) || !currentState.isAir()) &&
                attempts < 20 && mutable.getY() < topY) {
            mutable.move(0, 1, 0);
            currentState = world.getBlockState(mutable);
            attempts++;
        }

        // Now find solid ground below
        attempts = 0;
        while (mutable.getY() > bottomY && attempts < 30) {
            BlockState below = world.getBlockState(mutable.down());
            BlockState current = world.getBlockState(mutable);

            // Check if position below is solid and current position is safe
            if (below.isSolidBlock(world, mutable.down()) &&
                    (current.isAir() || current.isReplaceable() ||
                            current.getFluidState().isOf(Fluids.WATER))) {
                return mutable;
            }

            mutable.move(0, -1, 0);
            attempts++;
        }

        return null;
    }

    private static boolean isSafeTeleportLocation(ServerWorld world, BlockPos pos, boolean ownerInWater) {
        // Minecraft 1.21.11 world bounds
        int minY = -64;
        int maxY = 320;

        // Don't teleport into void or above world limit
        if (pos.getY() < minY || pos.getY() >= maxY) {
            return false;
        }

        BlockPos groundPos = pos.down();
        BlockPos bodyPos = pos;
        BlockPos headPos = pos.up();

        // Check if ground position is within world bounds
        if (groundPos.getY() < minY) {
            return false;
        }

        BlockState groundState = world.getBlockState(groundPos);
        BlockState bodyState = world.getBlockState(bodyPos);
        BlockState headState = world.getBlockState(headPos);

        // ===== GROUND CHECK =====
        // Ground must support the pet
        boolean groundIsSolid = groundState.isSolidBlock(world, groundPos);
        boolean groundIsWater = groundState.getFluidState().getFluid() == Fluids.WATER ||
                groundState.getFluidState().getFluid() == Fluids.FLOWING_WATER;

        // Ice is solid but has fluid properties
        boolean groundIsIce = groundState.isOf(net.minecraft.block.Blocks.ICE) ||
                groundState.isOf(net.minecraft.block.Blocks.PACKED_ICE) ||
                groundState.isOf(net.minecraft.block.Blocks.BLUE_ICE) ||
                groundState.isOf(net.minecraft.block.Blocks.FROSTED_ICE);

        // Special handling for water - allow pets to teleport into water if owner is in water
        if (ownerInWater && groundIsWater) {
            // Allow water as ground when owner is in water
        } else if (!groundIsSolid && !groundIsWater && !groundIsIce) {
            // Allow some other blocks that pets can stand on
            if (!groundState.isOf(net.minecraft.block.Blocks.LILY_PAD) &&
                    !(groundState.getBlock() instanceof LeavesBlock) &&
                    !groundState.isOf(net.minecraft.block.Blocks.SNOW_BLOCK) &&
                    !groundState.isOf(net.minecraft.block.Blocks.SNOW)) {
                return false;
            }
        }

        // ===== BODY SPACE CHECK =====
        // Body space must be clear or water (if owner is in water)
        boolean bodyIsAir = bodyState.isAir();
        boolean bodyIsReplaceable = bodyState.isReplaceable();
        boolean bodyIsWater = bodyState.getFluidState().getFluid() == Fluids.WATER ||
                bodyState.getFluidState().getFluid() == Fluids.FLOWING_WATER;

        if (ownerInWater && bodyIsWater) {
            // Allow water if owner is in water
        } else if (!bodyIsAir && !bodyIsReplaceable && !bodyIsWater) {
            return false;
        }

        // ===== HEAD SPACE CHECK =====
        // Head space must be clear
        boolean headIsAir = headState.isAir();
        boolean headIsReplaceable = headState.isReplaceable();
        boolean headIsWater = headState.getFluidState().getFluid() == Fluids.WATER ||
                headState.getFluidState().getFluid() == Fluids.FLOWING_WATER;

        if (ownerInWater && headIsWater) {
            // Allow water if owner is in water
        } else if (!headIsAir && !headIsReplaceable && !headIsWater) {
            return false;
        }

        // ===== DANGEROUS BLOCKS CHECK =====
        if (isDangerousBlock(groundState) || isDangerousBlock(bodyState) || isDangerousBlock(headState)) {
            return false;
        }

        // ===== FLUID CHECKS =====
        // Allow water but not lava
        if (groundState.getFluidState().isOf(Fluids.LAVA) ||
                bodyState.getFluidState().isOf(Fluids.LAVA) ||
                headState.getFluidState().isOf(Fluids.LAVA)) {
            return false;
        }

        return true;
    }

    private static boolean isDangerousBlock(BlockState state) {
        var block = state.getBlock();

        return block instanceof AbstractFireBlock ||
                block instanceof CampfireBlock ||
                block instanceof MagmaBlock ||
                block instanceof CactusBlock ||
                block instanceof SweetBerryBushBlock ||
                block instanceof WitherRoseBlock ||
                state.getBlock() == net.minecraft.block.Blocks.LAVA ||
                state.getBlock() == net.minecraft.block.Blocks.POWDER_SNOW ||
                state.getBlock() == net.minecraft.block.Blocks.SWEET_BERRY_BUSH;
    }
}
