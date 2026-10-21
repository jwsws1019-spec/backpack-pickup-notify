package io.github.bpnotify.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sophisticated Core's magnet upgrade moves items into the backpack and only shrinks the
 * item entity, without telling the client. After a successful insert we send the vanilla
 * "item taken" packet (via Player#take), which is what Loot Journal listens to.
 * All injections are optional (require = 0): if the target changes, the feature just stays off.
 */
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.upgrades.magnet.MagnetUpgradeWrapper", remap = false)
public abstract class MagnetUpgradeWrapperMixin {
    @Unique
    private static final Logger BPNOTIFY_LOGGER = LogUtils.getLogger();
    @Unique
    private static boolean bpnotify$errorLogged = false;

    @Unique
    private Entity bpnotify$collector;
    @Unique
    private int bpnotify$countBefore;

    @Inject(method = "pickupItems", at = @At("HEAD"), remap = false, require = 0)
    private void bpnotify$start(Entity entity, Level world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        this.bpnotify$collector = entity;
    }

    @Inject(method = "pickupItems", at = @At("RETURN"), remap = false, require = 0)
    private void bpnotify$end(Entity entity, Level world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        this.bpnotify$collector = null;
    }

    @Inject(method = "tryToInsertItem", at = @At("HEAD"), remap = false, require = 0)
    private void bpnotify$before(ItemEntity itemEntity, CallbackInfoReturnable<Boolean> cir) {
        try {
            this.bpnotify$countBefore = itemEntity.getItem().getCount();
        } catch (Throwable t) {
            bpnotify$logOnce(t);
        }
    }

    @Inject(method = "tryToInsertItem", at = @At("RETURN"), remap = false, require = 0)
    private void bpnotify$after(ItemEntity itemEntity, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (cir.getReturnValueZ() && this.bpnotify$collector instanceof ServerPlayer player) {
                int moved = this.bpnotify$countBefore - itemEntity.getItem().getCount();
                if (moved > 0) {
                    player.take(itemEntity, moved);
                }
            }
        } catch (Throwable t) {
            bpnotify$logOnce(t);
        }
    }

    @Unique
    private static void bpnotify$logOnce(Throwable t) {
        if (!bpnotify$errorLogged) {
            bpnotify$errorLogged = true;
            BPNOTIFY_LOGGER.error("[bpnotify] magnet notification failed (further errors are hidden)", t);
        }
    }
}
