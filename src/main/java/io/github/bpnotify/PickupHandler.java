package io.github.bpnotify;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Sophisticated Backpacks' pickup upgrade cancels the vanilla pickup event after it moved the
 * items into the backpack, so vanilla never sends the "item taken" packet. We measure how many
 * items disappeared from the item entity during the event and send that packet ourselves.
 */
@Mod.EventBusSubscriber(modid = BpNotify.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PickupHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean errorLogged = false;

    private static ItemEntity trackedEntity = null;
    private static int countBefore = -1;

    private PickupHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onPickupFirst(EntityItemPickupEvent event) {
        try {
            ItemEntity item = event.getItem();
            trackedEntity = item;
            countBefore = item.getItem().getCount();
        } catch (Throwable t) {
            logOnce(t);
            reset();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onPickupLast(EntityItemPickupEvent event) {
        try {
            ItemEntity item = event.getItem();
            if (item == trackedEntity && event.isCanceled() && event.getEntity() instanceof ServerPlayer player) {
                int moved = countBefore - item.getItem().getCount();
                if (moved > 0) {
                    player.take(item, moved);
                }
            }
        } catch (Throwable t) {
            logOnce(t);
        } finally {
            reset();
        }
    }

    private static void reset() {
        trackedEntity = null;
        countBefore = -1;
    }

    private static void logOnce(Throwable t) {
        if (!errorLogged) {
            errorLogged = true;
            LOGGER.error("[bpnotify] pickup notification failed (further errors are hidden)", t);
        }
    }
}
