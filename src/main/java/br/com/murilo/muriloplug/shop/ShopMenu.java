package br.com.murilo.muriloplug.shop;

import br.com.murilo.muriloplug.economy.EconomyManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Baú da loja (54 slots): 45 itens por página e, embaixo, a barra de navegação. */
public final class ShopMenu implements ShopHolder {

    public enum Mode { BUY, ADMIN }

    static final int ITEMS_PER_PAGE = 45;
    static final int PREVIOUS_SLOT = 45;
    static final int ADD_SLOT = 47;
    static final int BALANCE_SLOT = 49;
    static final int NEXT_SLOT = 53;

    private final Inventory inventory;
    private final Mode mode;
    private final ShopManager shop;
    private final EconomyManager economy;
    private final UUID viewer;
    /** Id do item de cada slot da página atual (-1 = vazio). */
    private final int[] itemIds = new int[ITEMS_PER_PAGE];
    private int page;

    private ShopMenu(Player viewer, ShopManager shop, EconomyManager economy, Mode mode, int page) {
        this.viewer = viewer.getUniqueId();
        this.shop = shop;
        this.economy = economy;
        this.mode = mode;
        this.page = page;
        this.inventory = Bukkit.createInventory(this, 54, Component.text(mode == Mode.BUY ? "Loja" : "Loja (admin)"));
        render();
    }

    public static void open(Player player, ShopManager shop, EconomyManager economy, Mode mode, int page) {
        player.openInventory(new ShopMenu(player, shop, economy, mode, page).getInventory());
    }

    public Mode mode() {
        return mode;
    }

    public int page() {
        return page;
    }

    /** Id do item no slot, ou -1 se o slot não tiver item. */
    public int itemIdAt(int slot) {
        return slot >= 0 && slot < ITEMS_PER_PAGE ? itemIds[slot] : -1;
    }

    public int pageCount() {
        return Math.max(1, (shop.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
    }

    /** Muda de página no próprio baú, sem reabrir. */
    public void changePage(int delta) {
        page += delta;
        render();
    }

    /** Redesenha o baú com o estado atual da loja. */
    public void render() {
        List<ShopItem> all = shop.list();
        page = Math.max(0, Math.min(page, pageCount() - 1));
        inventory.clear();
        Arrays.fill(itemIds, -1);

        int start = page * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEMS_PER_PAGE && start + i < all.size(); i++) {
            ShopItem shopItem = all.get(start + i);
            itemIds[i] = shopItem.id();
            inventory.setItem(i, ShopUi.display(shopItem, mode == Mode.ADMIN));
        }

        for (int slot = ITEMS_PER_PAGE; slot < 54; slot++) {
            inventory.setItem(slot, ShopUi.filler());
        }
        if (page > 0) {
            inventory.setItem(PREVIOUS_SLOT, ShopUi.button(Material.ARROW, "§ePágina anterior"));
        }
        if (page < pageCount() - 1) {
            inventory.setItem(NEXT_SLOT, ShopUi.button(Material.ARROW, "§ePróxima página"));
        }
        if (mode == Mode.ADMIN) {
            inventory.setItem(ADD_SLOT, ShopUi.button(Material.LIME_DYE, "§a§lAdicionar item"));
        }
        inventory.setItem(BALANCE_SLOT, ShopUi.button(Material.GOLD_INGOT,
                "§6Saldo: §a" + EconomyManager.format(economy.getBalance(viewer))));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
