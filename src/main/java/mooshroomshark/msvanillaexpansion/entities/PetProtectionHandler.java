package mooshroomshark.msvanillaexpansion.entities;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Handles protection of tamed pets from their owners unless sneaking
 */
public class PetProtectionHandler {

    public static void register() {
        AttackEntityCallback.EVENT.register(PetProtectionHandler::onAttackEntity);
    }

    private static ActionResult onAttackEntity(PlayerEntity player, World world, Hand hand, Entity entity, @Nullable EntityHitResult hitResult) {
        // Check if the entity being attacked is a tamed pet
        if (entity instanceof TameableEntity tameable) {
            // Check if it's one of our protected pets
            if (entity instanceof WolfEntity || entity instanceof CatEntity || entity instanceof ParrotEntity) {
                // Check if this pet is tamed and the player is the owner
                if (tameable.isTamed() && tameable.isOwner(player)) {
                    // If the player is NOT sneaking, prevent the attack
                    if (!player.isSneaking()) {
                        return ActionResult.FAIL;
                    }
                }
            }
        }

        return ActionResult.PASS;
    }
}
