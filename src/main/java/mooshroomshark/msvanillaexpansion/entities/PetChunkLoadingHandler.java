package mooshroomshark.msvanillaexpansion.entities;

import mooshroomshark.msvanillaexpansion.MooshroomSharksVanillaExpansion;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.WolfEntity;
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

    // Reduced teleport distance threshold - teleport if more than 32 blocks away
    private static final double TELEPORT_DISTANCE_SQUARED = 1024.0; // 32 blocks squared
    private static final double LOAD_CHUNK_DISTANCE_SQUARED = 256.0; // 16 blocks squared

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

            double distanceSquared = tameable.squaredDistanceTo(tameable.getOwner());
            double distance = Math.sqrt(distanceSquared);

            int petId = entity.getId();
            long currentTick = world.getTime();
            Long lastFailedAttempt = failedTeleportCooldowns.get(petId);
            boolean hasPendingRetry = lastFailedAttempt != null;

            // Always load chunk if pet is far from owner (more than 16 blocks)
            if (tameable.getOwner().getEntityWorld() != world ||
                    distanceSquared > LOAD_CHUNK_DISTANCE_SQUARED || hasPendingRetry) {

                ChunkPos chunkPos = new ChunkPos(entity.getBlockPos());
                int ticketCount = currentTickTickets.getOrDefault(chunkPos, 0) + 1;
                currentTickTickets.put(chunkPos, ticketCount);

                // Only add ticket if we haven't already for this chunk this tick
                if (ticketCount == 1) {
                    // Use the correct addTicket method for 1.21.11
                    world.getChunkManager().addTicket(PET_TICKET, chunkPos, 2);
                }
                petsLoadedChunks++;

                // Try to teleport if very far (more than 32 blocks) or has pending retry
                if (distanceSquared > TELEPORT_DISTANCE_SQUARED || hasPendingRetry) {
                    if (lastFailedAttempt == null || (currentTick - lastFailedAttempt) >= TELEPORT_RETRY_COOLDOWN) {

                        double targetX = tameable.getOwner().getX();
                        double targetY = tameable.getOwner().getY();
                        double targetZ = tameable.getOwner().getZ();

                        BlockPos safePos = findSafeTeleportLocation(world, targetX, targetY, targetZ);

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

                            MooshroomSharksVanillaExpansion.LOGGER.warn(
                                    "Could not find safe teleport location for {} near owner at ({}, {}, {}). Will retry in {} seconds.",
                                    entity.getType().getName().getString(),
                                    (int) targetX, (int) targetY, (int) targetZ,
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
        }

        // Update the global map with current tick's tickets
        petChunkTickets.clear();
        petChunkTickets.putAll(currentTickTickets);

        // Log status every 5 seconds (debug only)
        if (petsLoadedChunks > 0 && world.getTime() % 100 == 0) {
            MooshroomSharksVanillaExpansion.LOGGER.debug(
                    "Keeping {} chunks loaded for {} pets (found {} total pets, teleported {} this tick)",
                    petChunkTickets.size(), petsLoadedChunks, petsFound, petsTeleported
            );
        }
    }

    private static BlockPos findSafeTeleportLocation(ServerWorld world, double x, double y, double z) {
        BlockPos targetPos = BlockPos.ofFloored(x, y, z);

        // STRATEGY 1: Owner's exact position (if safe)
        if (isSafeTeleportLocation(world, targetPos)) {
            return targetPos;
        }

        // STRATEGY 2: Directly above owner (for water/swimming)
        for (int yOffset = 1; yOffset <= 4; yOffset++) {
            BlockPos checkPos = targetPos.add(0, yOffset, 0);
            if (isSafeTeleportLocation(world, checkPos)) {
                return checkPos;
            }
        }

        // STRATEGY 3: Horizontal search at same Y level
        for (int radius = 1; radius <= 6; radius++) {
            for (int xOffset = -radius; xOffset <= radius; xOffset++) {
                for (int zOffset = -radius; zOffset <= radius; zOffset++) {
                    // Check same Y level first
                    BlockPos checkPos = targetPos.add(xOffset, 0, zOffset);
                    if (isSafeTeleportLocation(world, checkPos)) {
                        return checkPos;
                    }
                }
            }
        }

        // STRATEGY 4: 3D search around player
        for (int radius = 1; radius <= 4; radius++) {
            for (int xOffset = -radius; xOffset <= radius; xOffset++) {
                for (int yOffset = -2; yOffset <= 4; yOffset++) {
                    for (int zOffset = -radius; zOffset <= radius; zOffset++) {
                        BlockPos checkPos = targetPos.add(xOffset, yOffset, zOffset);
                        if (isSafeTeleportLocation(world, checkPos)) {
                            return checkPos;
                        }
                    }
                }
            }
        }

        // STRATEGY 5: Last resort - find highest safe ground above
        for (int yOffset = 1; yOffset <= 20; yOffset++) {
            BlockPos checkPos = targetPos.add(0, yOffset, 0);
            if (isSafeTeleportLocation(world, checkPos)) {
                return checkPos;
            }
        }

        return null;
    }

    private static boolean isSafeTeleportLocation(ServerWorld world, BlockPos pos) {
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

        if (!groundIsSolid && !groundIsWater && !groundIsIce) {
            // Allow some other blocks that pets can stand on
            if (!groundState.isOf(net.minecraft.block.Blocks.LILY_PAD) &&
                    !(groundState.getBlock() instanceof LeavesBlock)) {
                return false;
            }
        }

        // ===== BODY SPACE CHECK =====
        // Body space must be clear
        boolean bodyIsAir = bodyState.isAir();
        boolean bodyIsReplaceable = bodyState.isReplaceable();
        boolean bodyIsWater = bodyState.getFluidState().getFluid() == Fluids.WATER ||
                bodyState.getFluidState().getFluid() == Fluids.FLOWING_WATER;

        if (!bodyIsAir && !bodyIsReplaceable && !bodyIsWater) {
            return false;
        }

        // ===== HEAD SPACE CHECK =====
        // Head space must be clear
        boolean headIsAir = headState.isAir();
        boolean headIsReplaceable = headState.isReplaceable();
        boolean headIsWater = headState.getFluidState().getFluid() == Fluids.WATER ||
                headState.getFluidState().getFluid() == Fluids.FLOWING_WATER;

        if (!headIsAir && !headIsReplaceable && !headIsWater) {
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