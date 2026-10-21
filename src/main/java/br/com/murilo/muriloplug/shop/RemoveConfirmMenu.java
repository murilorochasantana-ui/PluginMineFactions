package br.com.murilo.muriloplug.shop;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/** Confirmação de "Remover da loja" (verde remove, vermelho volta). */
public final class RemoveConfirmMenu implements ShopHolder {

    static final int CANCEL_SLOT = 3;
    static final int ITEM_SLOT = 4;
    static final int CONFIRM_SLOT = 5;

    private final Inventory inventory;
    private final int itemId;
    private final int backPage;
    private boolean used;

    private RemoveConfirmMenu(ShopItem shopItem, int backPage) {
        this.itemId = shopItem.id();
        this.backPage = backPage;
        this.inventory = Bukkit.createInventory(this, 9, Component.text("Remover da loja?"));
        inventory.setItem(CANCEL_SLOT, ShopUi.button(Material.RED_CONCRETE, "§cCancelar"));
        inventory.setItem(ITEM_SLOT, shopItem.item());
        inventory.setItem(CONFIRM_SLOT, ShopUi.button(Material.LIME_CONCRETE, "§aRemover"));
    }

    public static void open(Player player, ShopItem shopItem, int backPage) {
        player.openInventory(new RemoveConfirmMenu(shopItem, backPage).getInventory());
    }

    int itemId() {
        return itemId;
    }

    int backPage() {
        return backPage;
    }

    boolean used() {
        return used;
    }

    void markUsed() {
        used = true;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
