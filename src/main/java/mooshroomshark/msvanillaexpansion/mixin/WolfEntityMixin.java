package mooshroomshark.msvanillaexpansion.mixin;

import mooshroomshark.msvanillaexpansion.entities.WolfFixConfig;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WolfEntity.class)
public abstract class WolfEntityMixin {

    /**
     * Improved teleportation logic to prevent wolves from getting stuck
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void enhancedTeleportation(CallbackInfo ci) {
        WolfEntity wolf = (WolfEntity) (Object) this;
        World world = wolf.getEntityWorld();

        if (!WolfFixConfig.IMPROVED_TELEPORTATION || world.isClient() || wolf.isInSittingPose()) {
            return;
        }

        if (wolf.getOwner() instanceof PlayerEntity owner) {
            double distanceToOwner = wolf.squaredDistanceTo(owner);

            // If wolf is very far from owner (beyond max range), attempt teleport
            if (distanceToOwner > WolfFixConfig.MAX_TELEPORT_RANGE * WolfFixConfig.MAX_TELEPORT_RANGE) {
                // Try to find a safe position near the owner
                BlockPos ownerPos = owner.getBlockPos();

                for (int attempts = 0; attempts < 10; attempts++) {
                    int offsetX = wolf.getRandom().nextInt(7) - 3;
                    int offsetZ = wolf.getRandom().nextInt(7) - 3;
                    int offsetY = wolf.getRandom().nextInt(3) - 1;

                    BlockPos targetPos = ownerPos.add(offsetX, offsetY, offsetZ);

                    if (canTeleportTo(wolf, (ServerWorld) world, targetPos)) {
                        wolf.refreshPositionAndAngles(
                                targetPos.getX() + 0.5,
                                targetPos.getY(),
                                targetPos.getZ() + 0.5,
                                wolf.getYaw(),
                                wolf.getPitch()
                        );
                        wolf.setVelocity(Vec3d.ZERO);
                        wolf.fallDistance = 0;
                        break;
                    }
                }
            }
        }
    }

    /**
     * Check if a position is safe for teleportation
     */
    private boolean canTeleportTo(WolfEntity wolf, ServerWorld world, BlockPos pos) {
        if (!world.isInBuildLimit(pos)) {
            return false;
        }

        // Check if blocks are loaded
        if (!world.isChunkLoaded(pos)) {
            return false;
        }

        // Check if there's solid ground and enough space
        BlockPos groundPos = pos.down();
        BlockPos abovePos = pos.up();

        return world.getBlockState(groundPos).isSolid() &&
                world.getBlockState(pos).isAir() &&
                world.getBlockState(abovePos).isAir() &&
                !world.getBlockState(pos).isLiquid();
    }
}
