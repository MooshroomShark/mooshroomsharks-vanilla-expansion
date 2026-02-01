package mooshroomshark.msvanillaexpansion.entities;

import mooshroomshark.msvanillaexpansion.MooshroomSharksVanillaExpansion;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.*;
import net.minecraft.fluid.Fluids;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.*;

public class PetChunkLoadingHandler {

    private static final Map<ChunkPos, Integer> petChunkTickets = new HashMap<>();
    private static final Map<Integer, Long> failedTeleportCooldowns = new HashMap<>();

    private static final long TELEPORT_RETRY_COOLDOWN = 100;
    private static final double TELEPORT_DISTANCE_SQUARED = 4096.0;

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
        Map<ChunkPos, Integer> currentTickTickets = new HashMap<>();

        List<Entity> pets = new ArrayList<>();
        for (Entity entity : world.iterateEntities()) {
            if (entity instanceof TameableEntity tameable &&
                    (entity instanceof WolfEntity || entity instanceof CatEntity || entity instanceof ParrotEntity) &&
                    tameable.isTamed() &&
                    !tameable.isSitting() &&
                    tameable.getOwner() != null) {

                pets.add(entity);
            }
        }

        for (Entity pet : pets) {
            TameableEntity tameable = (TameableEntity) pet;
            Entity owner = tameable.getOwner();

            ChunkPos petChunkPos = new ChunkPos(pet.getBlockPos());
            ChunkPos ownerChunkPos = new ChunkPos(owner.getBlockPos());

            for (ChunkPos chunkPos : new ChunkPos[]{petChunkPos, ownerChunkPos}) {
                int ticketCount = currentTickTickets.getOrDefault(chunkPos, 0) + 1;
                currentTickTickets.put(chunkPos, ticketCount);
                if (ticketCount == 1) {
                    world.getChunkManager().addTicket(PET_TICKET, chunkPos, 3);
                }
            }

            double distanceSquared = pet.squaredDistanceTo(owner);
            long currentTick = world.getTime();
            int petId = pet.getId();

            Long lastFailed = failedTeleportCooldowns.get(petId);
            boolean hasPendingRetry = lastFailed != null;

            if (distanceSquared > TELEPORT_DISTANCE_SQUARED || hasPendingRetry) {
                if (lastFailed == null || currentTick - lastFailed >= TELEPORT_RETRY_COOLDOWN) {

                    boolean ownerInWater = owner.isTouchingWater() ||
                            world.getFluidState(owner.getBlockPos()).isOf(Fluids.WATER);

                    BlockPos safePos = findSafeTeleportLocation(
                            world,
                            owner.getX(),
                            owner.getY(),
                            owner.getZ(),
                            ownerInWater
                    );

                    if (safePos != null) {
                        failedTeleportCooldowns.remove(petId);

                        pet.teleport(
                                world,
                                safePos.getX() + 0.5,
                                safePos.getY(),
                                safePos.getZ() + 0.5,
                                Set.of(),
                                pet.getYaw(),
                                pet.getPitch(),
                                false
                        );

                        MooshroomSharksVanillaExpansion.LOGGER.info(
                                "Teleported {} to owner at {}",
                                pet.getType().getName().getString(),
                                safePos
                        );
                    } else {
                        failedTeleportCooldowns.put(petId, currentTick);
                    }
                }
            }
        }

        petChunkTickets.clear();
        petChunkTickets.putAll(currentTickTickets);
    }

    /* ---------- TELEPORT SAFETY ---------- */

    private static BlockPos findSafeTeleportLocation(ServerWorld world, double x, double y, double z, boolean ownerInWater) {
        BlockPos pos = BlockPos.ofFloored(x, y, z);
        if (isSafeTeleportLocation(world, pos, ownerInWater)) return pos;
        return findSurfacePosition(world, pos);
    }

    private static BlockPos findSurfacePosition(ServerWorld world, BlockPos pos) {
        BlockPos.Mutable m = pos.mutableCopy();
        for (int i = 0; i < 40; i++) {
            if (world.getBlockState(m).isAir() &&
                    world.getBlockState(m.down()).isSolidBlock(world, m.down())) return m;
            m.move(0, 1, 0);
        }
        return null;
    }

    private static boolean isSafeTeleportLocation(ServerWorld world, BlockPos pos, boolean ownerInWater) {
        BlockState ground = world.getBlockState(pos.down());
        BlockState body = world.getBlockState(pos);
        BlockState head = world.getBlockState(pos.up());

        if (ground.getFluidState().isOf(Fluids.LAVA)) return false;
        if (!ground.isSolidBlock(world, pos.down()) && !ownerInWater) return false;
        if (!body.isAir() && !body.isReplaceable()) return false;
        if (!head.isAir() && !head.isReplaceable()) return false;

        return true;
    }
}
