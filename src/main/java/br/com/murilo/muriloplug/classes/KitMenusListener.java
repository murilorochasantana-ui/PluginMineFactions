package br.com.murilo.muriloplug.classes;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Cliques do menu de kits. Todo clique e arrasto é cancelado: nada pode ser pego, movido ou colocado. */
public final class KitMenusListener implements Listener {

    private final ClassItems items;
    private final ServerItems serverItems;

    public KitMenusListener(ClassItems items, ServerItems serverItems) {
        this.items = items;
        this.serverItems = serverItems;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof ServerItemsMenu serverMenu) {
            onServerItemsClick(event, serverMenu);
            return;
        }
        if (!(holder instanceof KitMenu menu)) {
            return;
        }
        // Cancela tudo (shift-click, teclas 1-9, tecla F, clique duplo, cliques no inventário do jogador).
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        if (event.getSlot() == KitMenu.SERVER_ITEMS_SLOT) {
            if (!player.hasPermission(KitCommand.PERMISSION)) {
                player.sendMessage("§cVocê não tem permissão.");
                return;
            }
            ServerItemsMenu.open(player, serverItems, null);
            return;
        }
        PlayerClass clicked = menu.classAt(event.getSlot());
        if (clicked == null) {
            return;
        }
        if (!player.hasPermission(KitCommand.PERMISSION)) {
            player.sendMessage("§cVocê não tem permissão.");
            return;
        }
        give(player, clicked);
    }

    /** "Itens do Servidor": clique = 1, Shift + clique = um pack; o item vem sempre novo da fábrica (o menu não muda). */
    private void onServerItemsClick(InventoryClickEvent event, ServerItemsMenu menu) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()
                || (!event.getClick().isLeftClick() && !event.getClick().isRightClick())) {
            return;
        }
        if (!player.hasPermission(KitCommand.PERMISSION)) {
            player.sendMessage("§cVocê não tem permissão.");
            player.closeInventory();
            return;
        }
        int slot = event.getSlot();
        if (slot == ServerItemsMenu.BACK_SLOT) {
            if (menu.category() != null) {
                ServerItemsMenu.open(player, serverItems, null);
            } else {
                KitMenu.open(player, items, serverItems);
            }
            return;
        }
        if (menu.category() != null && (slot == ServerItemsMenu.PREVIOUS_SLOT || slot == ServerItemsMenu.NEXT_SLOT)) {
            ServerItemsMenu.open(player, serverItems, menu.category(),
                    menu.page() + (slot == ServerItemsMenu.NEXT_SLOT ? 1 : -1));
            return;
        }
        if (menu.category() == null && slot == ServerItemsMenu.ALL_SLOT) {
            giveAll(player, serverItems.all());
            player.sendMessage("§aVocê pegou 1 de cada Item do Servidor.");
            return;
        }
        ServerItems.Category category = menu.categoryAt(slot);
        if (category != null) {
            ServerItemsMenu.open(player, serverItems, category);
            return;
        }
        java.util.function.Supplier<ItemStack> factory = menu.factoryAt(slot);
        if (factory == null) {
            return;
        }
        ItemStack item = factory.get();
        if (event.isShiftClick()) {
            item.setAmount(Math.max(1, item.getMaxStackSize()));
        }
        giveAll(player, List.of(item));
    }

    private static void giveAll(Player player, List<ItemStack> kit) {
        boolean dropped = false;
        for (ItemStack item : kit) {
            for (ItemStack leftover : player.getInventory().addItem(item).values()) {
                player.getWorld().dropItem(player.getLocation(), leftover);
                dropped = true;
            }
        }
        if (dropped) {
            player.sendMessage("§eInventário cheio: alguns itens caíram no chão.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof KitMenu
                || event.getView().getTopInventory().getHolder() instanceof ServerItemsMenu) {
            event.setCancelled(true);
        }
    }

    /** Coloca o kit no inventário; o que não couber cai aos pés do jogador. */
    private void give(Player player, PlayerClass playerClass) {
        List<ItemStack> kit = items.getItems(playerClass);
        boolean dropped = false;
        for (ItemStack item : kit) {
            for (ItemStack leftover : player.getInventory().addItem(item).values()) {
                player.getWorld().dropItem(player.getLocation(), leftover);
                dropped = true;
            }
        }
        if (dropped) {
            player.sendMessage("§eInventário cheio: alguns itens caíram no chão.");
        }
        player.sendMessage("§aVocê pegou o kit de " + playerClass.coloredName() + "§a.");
    }
}
