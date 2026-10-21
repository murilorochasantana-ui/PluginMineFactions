package br.com.murilo.muriloplug.shop;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** Conferência de espaço no inventário, usada antes de cobrar ou de devolver um item. */
public final class InventorySpace {

    private InventorySpace() {
    }

    /** Se o item inteiro cabe no inventário (espaço vazio ou pilhas iguais com espaço sobrando). */
    public static boolean fits(PlayerInventory inventory, ItemStack item) {
        int remaining = item.getAmount();
        int max = item.getMaxStackSize();
        for (ItemStack slot : inventory.getStorageContents()) {
            if (slot == null || slot.getType().isAir()) {
                remaining -= max;
            } else if (slot.isSimilar(item)) {
                remaining -= Math.max(0, max - slot.getAmount());
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }
}
