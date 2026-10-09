package com.skycraft.inventory.client;

import com.skycraft.Skycraft;
import com.skycraft.client.ClientPacketHandlers;
import com.skycraft.inventory.InventoryConfig;
import com.skycraft.inventory.ItemWeights;
import com.skycraft.network.CorePackets;
import com.skycraft.network.NotifyKind;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Forge-bus client events of the inventory module: swapping the vanilla survival inventory for the Skyrim one, the
 * favorites key, keeping an over-encumbered player from running, and item weights in tooltips.
 */
@Mod.EventBusSubscriber(modid = Skycraft.MODID, value = Dist.CLIENT)
public final class InventoryClientEvents {
    /** Class of the world module's map screen (fast travel), matched by name to avoid a hard dependency. */
    private static final String MAP_SCREEN = "com.skycraft.world.client.MapScreen";

    /** One-shot: the next vanilla InventoryScreen is wanted (opened from the Skyrim inventory's Crafting button). */
    private static boolean vanillaOnce;

    private InventoryClientEvents() {}

    static void allowVanillaOnce() {
        vanillaOnce = true;
    }

    private static boolean replaceInventory() {
        try {
            return InventoryConfig.REPLACE_INVENTORY.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    // ------------------------------------------------------------------ screens

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Screen next = event.getNewScreen();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || next == null) return;

        if (next.getClass() == InventoryScreen.class) {
            if (!player.isCreative() && !player.isSpectator()) {
                // If looking at an item in the world, [E] takes/steals it without opening menus
                var lookedItem = com.skycraft.crime.client.CrimeHud.getLookedAtItem(mc, 3.5);
                if (lookedItem != null) {
                    event.setCanceled(true);
                    com.skycraft.network.SkyNetwork.sendToServer(new CorePackets.Action(CorePackets.Action.TAKE_WORLD_ITEM, lookedItem.getId()));
                    return;
                }

                if (mc.crosshairPickEntity instanceof net.minecraft.world.entity.LivingEntity le && le.isAlive()
                        && (le instanceof net.minecraft.world.entity.npc.AbstractVillager || le instanceof com.skycraft.society.entity.NpcEntity)
                        && !player.isShiftKeyDown()) {
                    event.setCanceled(true);
                    if (mc.gameMode != null) {
                        mc.gameMode.interact(player, le, net.minecraft.world.InteractionHand.MAIN_HAND);
                    }
                    return;
                }
                event.setNewScreen(new com.skycraft.client.screen.HubScreen());
            }
            return;
        }

        if (next instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> acs
                && acs.getMenu() instanceof net.minecraft.world.inventory.ChestMenu cm) {
            if (!player.isCreative() && !player.isSpectator()) {
                event.setNewScreen(new SkyrimContainerScreen(cm, player.getInventory(), acs.getTitle()));
                return;
            }
        }

        // The world map is where fast travel happens: remind an over-encumbered player they can't.
        if (next.getClass().getName().equals(MAP_SCREEN) && ClientInventoryHandlers.over && !player.isCreative()) {
            ClientPacketHandlers.notify(new CorePackets.Notify(NotifyKind.MESSAGE,
                    Component.translatable("inventory.skycraft.overencumbered_travel"), Component.empty(), 0, 0f));
        }
    }

    /** A way back from the vanilla inventory to the Skyrim one. */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen().getClass() != InventoryScreen.class || !replaceInventory()) return;
        Minecraft mc = Minecraft.getInstance();
        Component label = Component.translatable("inventory.skycraft.open_skyrim");
        int w = mc.font.width(label) + 16;
        event.addListener(Button.builder(label, b -> mc.setScreen(new SkyrimInventoryScreen())).bounds(4, 4, w, 16).build());
    }

    // ------------------------------------------------------------------ keys & sprinting

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        while (InventoryKeys.FAVORITES.consumeClick()) {
            if (mc.screen == null && !player.isSpectator()) mc.setScreen(new FavoritesScreen());
        }

        // Favorites hotbar mapping: number keys 1-8 map to Favorites 1-8
        if (mc.options != null && mc.options.keyHotbarSlots != null && !player.isCreative() && mc.screen == null) {
            for (int i = 0; i < Math.min(8, mc.options.keyHotbarSlots.length); i++) {
                if (mc.options.keyHotbarSlots[i].consumeClick()) {
                    List<InvEntry> favs = getFavorites(player);
                    if (i < favs.size()) {
                        if (com.skycraft.combat.Sheathe.isSheathed(player)) {
                            com.skycraft.network.SkyNetwork.sendToServer(new CorePackets.Action(CorePackets.Action.SHEATHE_TOGGLE, 0));
                        }
                        ClientInventoryHandlers.primary(favs.get(i));
                    }
                }
            }
            for (int i = 8; i < mc.options.keyHotbarSlots.length; i++) {
                while (mc.options.keyHotbarSlots[i].consumeClick()) {}
            }
        }

        // Over-encumbered: no running.
        if (ClientInventoryHandlers.over && !player.isCreative() && !player.isSpectator()) {
            if (player.isSprinting()) player.setSprinting(false);
            mc.options.keySprint.setDown(false);
        }
    }

    private static int scrollFavIndex = -1;

    public static List<InvEntry> getFavorites(LocalPlayer player) {
        List<InvEntry> list = new java.util.ArrayList<>();
        for (InvEntry e : InvEntry.build(player)) {
            if (e.favorite) list.add(e);
        }
        list.sort(java.util.Comparator.comparingInt((InvEntry e) -> e.category.ordinal())
                .thenComparing(e -> e.name, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(e -> e.equip));
        return list;
    }

    @SubscribeEvent
    public static void onMouseScroll(net.minecraftforge.client.event.InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null || player.isCreative() || player.isSpectator()) return;
        double delta = event.getScrollDelta();
        if (delta == 0) return;
        event.setCanceled(true);

        List<InvEntry> favs = getFavorites(player);
        if (favs.isEmpty()) {
            com.skycraft.network.SkyNetwork.sendToServer(new CorePackets.Action(CorePackets.Action.SHEATHE_TOGGLE, 0));
            for (int i = 0; i < 9; i++) {
                if (player.getInventory().getItem(i).isEmpty()) {
                    player.getInventory().selected = i;
                    break;
                }
            }
            boolean sheathed = !com.skycraft.combat.Sheathe.isSheathed(player);
            ClientPacketHandlers.notify(new CorePackets.Notify(NotifyKind.MESSAGE, Component.literal(sheathed ? "Hands Sheathed" : "Hands Drawn"), Component.empty(), 0, 0f));
            return;
        }

        int total = favs.size() + 1;
        if (scrollFavIndex < 0) scrollFavIndex = 0;
        if (delta > 0) {
            scrollFavIndex = Math.floorMod(scrollFavIndex - 1, total);
        } else {
            scrollFavIndex = Math.floorMod(scrollFavIndex + 1, total);
        }

        if (scrollFavIndex == favs.size()) {
            com.skycraft.network.SkyNetwork.sendToServer(new CorePackets.Action(CorePackets.Action.SHEATHE_TOGGLE, 0));
            for (int i = 0; i < 9; i++) {
                if (player.getInventory().getItem(i).isEmpty()) {
                    player.getInventory().selected = i;
                    break;
                }
            }
            ClientPacketHandlers.notify(new CorePackets.Notify(NotifyKind.MESSAGE, Component.literal("Hands Sheathed"), Component.empty(), 0, 0f));
        } else {
            InvEntry chosen = favs.get(scrollFavIndex);
            if (com.skycraft.combat.Sheathe.isSheathed(player)) {
                com.skycraft.network.SkyNetwork.sendToServer(new CorePackets.Action(CorePackets.Action.SHEATHE_TOGGLE, 0));
            }
            ClientInventoryHandlers.primary(chosen);
        }
    }

    /** Keep the forward impulse under the sprint threshold so neither the sprint key nor double-tapping W can start a run. */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!ClientInventoryHandlers.over || event.getEntity().isCreative() || event.getEntity().isSpectator()) return;
        Input input = event.getInput();
        if (input.forwardImpulse > 0.79f) input.forwardImpulse = 0.79f;
    }

    // ------------------------------------------------------------------ misc

    /** Skyrim shows every item's weight. */
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        float weight = ItemWeights.get(stack);
        if (weight <= 0f) return;
        event.getToolTip().add(Component.translatable("inventory.skycraft.tooltip.weight", ItemWeights.format(weight)).withStyle(ChatFormatting.GRAY));
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientInventoryHandlers.reset();
        vanillaOnce = false;
    }
}
