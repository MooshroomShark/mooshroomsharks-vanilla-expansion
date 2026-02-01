package mooshroomshark.msvanillaexpansion.mixin;

import mooshroomshark.msvanillaexpansion.MooshroomSharksVanillaExpansion;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.entity.vehicle.ChestBoatEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractBoatEntity.class)
public abstract class AbstractBoatEntityMixin {

    /**
     * Runs every tick for boats.
     * Handles pet boarding and pet dismounting safely INSIDE the boat.
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void msve$handlePetRiding(CallbackInfo ci) {
        AbstractBoatEntity boat = (AbstractBoatEntity) (Object) this;

        if (boat.getEntityWorld().isClient()) return;

        ServerWorld world = (ServerWorld) boat.getEntityWorld();

        // Find owner in this boat
        Entity owner = null;
        for (Entity passenger : boat.getPassengerList()) {
            if (passenger instanceof net.minecraft.entity.player.PlayerEntity) {
                owner = passenger;
                break;
            }
        }

        // No owner riding → kick pets
        if (owner == null) {
            for (Entity passenger : boat.getPassengerList()) {
                if (passenger instanceof TameableEntity tameable && tameable.isTamed()) {
                    MooshroomSharksVanillaExpansion.LOGGER.info(
                            "Mixin dismounting pet {} (owner left boat)",
                            passenger.getName().getString()
                    );
                    passenger.stopRiding();
                }
            }
            return;
        }

        // Owner riding → try to board pets
        boolean isChestBoat = boat instanceof ChestBoatEntity;
        int maxPassengers = isChestBoat ? 1 : 2;

        if (boat.getPassengerList().size() >= maxPassengers) return;

        for (Entity e : world.iterateEntities()) {
            if (!(e instanceof TameableEntity tameable)) continue;
            if (!tameable.isTamed()) continue;
            if (tameable.getOwner() != owner) continue;
            if (e.getVehicle() != null) continue;
            if (e.squaredDistanceTo(boat) > 100) continue;

            boolean success = e.startRiding(boat);
            if (success) {
                MooshroomSharksVanillaExpansion.LOGGER.info(
                        "Mixin boarded pet {} into boat",
                        e.getName().getString()
                );
            }
            return;
        }
    }
}
