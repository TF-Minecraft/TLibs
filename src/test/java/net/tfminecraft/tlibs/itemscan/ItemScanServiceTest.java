package net.tfminecraft.tlibs.itemscan;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Registry;
import io.papermc.paper.registry.RegistryAccess;
import org.bukkit.Server;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class ItemScanServiceTest {
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<HandlerList> handlerLists;
    private JavaPlugin plugin;
    private PluginManager pluginManager;
    private BukkitScheduler scheduler;
    private BukkitTask task;
    private ItemScanService service;
    private Runnable pulse;

    @BeforeAll
    static void initializeInventoryTypes() {
        // Paper initializes menu registries when InventoryType loads. These tests
        // exercise traversal only, so menu contents are deliberately unused.
        try (MockedStatic<RegistryAccess> registries = mockStatic(RegistryAccess.class)) {
            RegistryAccess access = mock(RegistryAccess.class, invocation -> mock(Registry.class));
            registries.when(RegistryAccess::registryAccess).thenReturn(access);
            InventoryType.values();
        }
    }

    @BeforeEach
    void start() {
        bukkit = mockStatic(Bukkit.class);
        handlerLists = mockStatic(HandlerList.class);
        plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        pluginManager = mock(PluginManager.class);
        scheduler = mock(BukkitScheduler.class);
        task = mock(BukkitTask.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(0L), eq(2L))).thenReturn(task);
        ItemScanService.start(plugin);
        service = ItemScanService.get();
        ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskTimer(eq(plugin), callback.capture(), eq(0L), eq(2L));
        pulse = callback.getValue();
    }

    @AfterEach
    void stop() {
        if (ItemScanService.get() != null) ItemScanService.get().stop();
        handlerLists.close();
        bukkit.close();
    }

    @Test
    void restartCancelsOldTaskUnregistersListenerAndDiscardsSubscriptions() {
        verify(pluginManager).registerEvents(service, plugin);
        ItemScanHandler handler = mock(ItemScanHandler.class);
        service.subscribe(handler);
        ItemScanService.start(plugin);
        assertNotSame(service, ItemScanService.get());
        verify(task).cancel();
        handlerLists.verify(() -> HandlerList.unregisterAll(service));
        pulse.run();
        verifyNoInteractions(handler);
        ItemScanService.get().stop();
        assertNull(ItemScanService.get());
        service.stop(); // Stopping an old instance is harmless.
    }

    @Test
    void pulsesVisitOnePlayerAtATimeAndSkipPersonalCraftingGrid() {
        Player first = playerWithPersonalView(InventoryType.CRAFTING);
        Player second = playerWithPersonalView(InventoryType.CREATIVE);
        ItemStack firstItem = item(Material.STONE);
        ItemStack secondItem = item(Material.DIRT);
        when(first.getInventory().getSize()).thenReturn(1);
        when(first.getInventory().getItem(0)).thenReturn(firstItem);
        when(second.getInventory().getSize()).thenReturn(1);
        when(second.getInventory().getItem(0)).thenReturn(secondItem);
        ItemScanHandler handler = matchingHandler();
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(first, second));
        pulse.run();
        verify(handler).update(first, first.getInventory(), 0, firstItem);
        verify(handler, never()).update(eq(second), any(), anyInt(), any());
        pulse.run();
        verify(handler).update(second, second.getInventory(), 0, secondItem);
        pulse.run();
        verify(handler, times(2)).update(first, first.getInventory(), 0, firstItem);
        verify(first.getOpenInventory().getTopInventory(), never()).getItem(anyInt());
        verify(second.getOpenInventory().getTopInventory(), never()).getItem(anyInt());
    }

    @Test
    void openScansTopThenPlayerSlotsInRegistrationOrderAndIgnoresEmptySlots() {
        Player player = playerWithPersonalView(InventoryType.CHEST);
        Inventory top = player.getOpenInventory().getTopInventory();
        ItemStack stone = item(Material.STONE);
        ItemStack dirt = item(Material.DIRT);
        when(top.getSize()).thenReturn(3);
        when(top.getItem(0)).thenReturn(stone);
        ItemStack air = item(Material.AIR);
        when(top.getItem(1)).thenReturn(air);
        when(player.getInventory().getSize()).thenReturn(1);
        when(player.getInventory().getItem(0)).thenReturn(dirt);
        ItemScanHandler first = matchingHandler();
        ItemScanHandler second = matchingHandler();
        service.subscribe(first); // Duplicate subscription must not duplicate callbacks.
        service.subscribe(null);
        service.onInventoryOpen(new InventoryOpenEvent(player.getOpenInventory()));
        var order = inOrder(first, second);
        order.verify(first).matches(stone);
        order.verify(first).update(player, top, 0, stone);
        order.verify(second).matches(stone);
        order.verify(second).update(player, top, 0, stone);
        order.verify(first).matches(dirt);
        order.verify(first).update(player, player.getInventory(), 0, dirt);
        order.verify(second).matches(dirt);
        order.verify(second).update(player, player.getInventory(), 0, dirt);
        order.verifyNoMoreInteractions();
        clearInvocations(first, second);
        service.unsubscribe(first);
        service.onInventoryOpen(new InventoryOpenEvent(player.getOpenInventory()));
        verifyNoInteractions(first);
        verify(second).update(player, top, 0, stone);
    }

    @Test
    void pluginOwnedMenusAreSkippedWhilePlayerInventoryIsScanned() {
        Player player = playerWithPersonalView(InventoryType.CHEST);
        Inventory top = player.getOpenInventory().getTopInventory();
        when(top.getHolder()).thenReturn(mock(InventoryHolder.class));
        when(top.getSize()).thenReturn(1);
        ItemStack stack = item(Material.STONE);
        when(top.getItem(0)).thenReturn(stack);
        when(player.getInventory().getSize()).thenReturn(1);
        when(player.getInventory().getItem(0)).thenReturn(stack);
        ItemScanHandler handler = matchingHandler();
        service.onInventoryOpen(new InventoryOpenEvent(player.getOpenInventory()));
        verify(top, never()).getItem(anyInt());
        verify(handler).update(player, player.getInventory(), 0, stack);
    }

    @Test
    void pickupUsesDetachedSlotAndWritesMutatedStackBackEvenWhenEventCancelled() {
        Player player = mock(Player.class);
        Item entity = mock(Item.class);
        ItemStack stack = item(Material.STONE);
        when(entity.getItemStack()).thenReturn(stack);
        ItemScanHandler handler = matchingHandler();
        doAnswer(invocation -> { stack.setAmount(2); return null; })
                .when(handler).update(player, null, -1, stack);
        EntityPickupItemEvent event = new EntityPickupItemEvent(player, entity, 0);
        event.setCancelled(true); // Preserve the original listener's cancellation semantics.
        service.onPickup(event);
        verify(handler).update(player, null, -1, stack);
        verify(stack).setAmount(2);
        verify(entity).setItemStack(stack);
    }

    @Test
    void nonMatchingHandlersDoNotUpdateItems() {
        Player player = mock(Player.class);
        Item entity = mock(Item.class);
        ItemStack stack = item(Material.STONE);
        when(entity.getItemStack()).thenReturn(stack);
        ItemScanHandler handler = mock(ItemScanHandler.class);
        service.subscribe(handler);
        service.onPickup(new EntityPickupItemEvent(player, entity, 0));
        verify(handler).matches(stack);
        verify(handler, never()).update(any(), any(), anyInt(), any());
    }

    private ItemScanHandler matchingHandler() {
        ItemScanHandler handler = mock(ItemScanHandler.class);
        when(handler.matches(any())).thenReturn(true);
        service.subscribe(handler);
        return handler;
    }

    private Player playerWithPersonalView(InventoryType type) {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        InventoryView view = mock(InventoryView.class);
        Inventory top = mock(Inventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getHolder()).thenReturn(player);
        when(player.getOpenInventory()).thenReturn(view);
        when(view.getPlayer()).thenReturn(player);
        when(view.getTopInventory()).thenReturn(top);
        when(top.getType()).thenReturn(type);
        return player;
    }

    private ItemStack item(Material material) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(material);
        return item;
    }
}
