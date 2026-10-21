package br.com.murilo.muriloplug.classes.mago;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/** O Grimório não pode ser usado em bigorna, mesa de encantamento, rebolo nem ferraria: nem entra neles. */
public final class GrimoireRestrictionsListener implements Listener {

    private static final Set<InventoryType> BLOCKED = Set.of(
            InventoryType.ANVIL, InventoryType.ENCHANTING, InventoryType.GRINDSTONE, InventoryType.SMITHING);

    private final GrimoireItems items;

    public GrimoireRestrictionsListener(GrimoireItems items) {
        this.items = items;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!BLOCKED.contains(event.getView().getTopInventory().getType())) {
            return;
        }
        ItemStack hotbar = event.getHotbarButton() >= 0
                ? event.getWhoClicked().getInventory().getItem(event.getHotbarButton()) : null;
        if (!items.isGrimoire(event.getCurrentItem()) && !items.isGrimoire(event.getCursor())
                && !items.isGrimoire(hotbar)) {
            return;
        }
        // Só mexer no Grimório dentro do próprio inventário, com clique simples, continua permitido.
        boolean plainBottomClick = event.getClickedInventory() != null
                && event.getClickedInventory() != event.getView().getTopInventory()
                && !event.getClick().isShiftClick() && event.getHotbarButton() < 0
                && event.getAction() != InventoryAction.COLLECT_TO_CURSOR;
        if (!plainBottomClick) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (BLOCKED.contains(event.getView().getTopInventory().getType()) && items.isGrimoire(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    // Garantias extras: se o item chegar lá por outro caminho, não há resultado.
    @EventHandler
    public void onAnvil(PrepareAnvilEvent event) {
        if (hasGrimoire(event.getInventory().getContents())) {
            event.setResult(null);
        }
    }

    @EventHandler
    public void onGrindstone(PrepareGrindstoneEvent event) {
        if (hasGrimoire(event.getInventory().getContents())) {
            event.setResult(null);
        }
    }

    @EventHandler
    public void onSmithing(PrepareSmithingEvent event) {
        if (hasGrimoire(event.getInventory().getContents())) {
            event.setResult(null);
        }
    }

    @EventHandler
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        if (items.isGrimoire(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEnchant(EnchantItemEvent event) {
        if (items.isGrimoire(event.getItem())) {
            event.setCancelled(true);
        }
    }

    private boolean hasGrimoire(ItemStack[] contents) {
        for (ItemStack item : contents) {
            if (items.isGrimoire(item)) {
                return true;
            }
        }
        return false;
    }
}
