package br.com.murilo.muriloplug.shop;

import br.com.murilo.muriloplug.economy.EconomyManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/** Confirmação de compra. Guarda o id e o preço que o jogador viu, para conferir antes de cobrar. */
public final class PurchaseConfirmMenu implements ShopHolder {

    static final int CANCEL_SLOT = 3;
    static final int ITEM_SLOT = 4;
    static final int CONFIRM_SLOT = 5;

    private final Inventory inventory;
    private final int itemId;
    private final long price;
    private final int page;
    /** Vira true na primeira compra concluída, para um clique duplo não comprar duas vezes. */
    private boolean used;

    private PurchaseConfirmMenu(ShopItem shopItem, int page, String[] moneyLore) {
        this.itemId = shopItem.id();
        this.price = shopItem.price();
        this.page = page;
        this.inventory = Bukkit.createInventory(this, 9, Component.text("Comprar?"));
        inventory.setItem(CANCEL_SLOT, ShopUi.button(Material.RED_CONCRETE, "§cCancelar"));
        inventory.setItem(ITEM_SLOT, shopItem.item());
        inventory.setItem(CONFIRM_SLOT, ShopUi.button(Material.LIME_CONCRETE,
                "§aComprar por " + EconomyManager.format(shopItem.price()), moneyLore));
    }

    public static void open(Player player, ShopItem shopItem, int page, EconomyManager economy) {
        player.openInventory(new PurchaseConfirmMenu(shopItem, page, ShopUi.moneyLore(player, economy)).getInventory());
    }

    int itemId() {
        return itemId;
    }

    long price() {
        return price;
    }

    int page() {
        return page;
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
