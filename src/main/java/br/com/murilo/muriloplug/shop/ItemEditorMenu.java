package br.com.murilo.muriloplug.shop;

import br.com.murilo.muriloplug.economy.EconomyManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Editor de item da loja (54 slots). O estado fica aqui no holder: item escolhido, preço,
 * qual item está sendo editado (null = item novo) e a página da loja para onde voltar.
 */
public final class ItemEditorMenu implements ShopHolder {

    static final int ITEM_SLOT = 13;
    static final int FIRST_PRICE_SLOT = PriceButtons.FIRST_SLOT;
    static final int PRICE_NAME_SLOT = 31;
    static final int RESET_SLOT = 40;
    static final int REMOVE_SLOT = 49;
    static final int CANCEL_SLOT = 45;
    static final int SAVE_SLOT = 53;

    private final Inventory inventory;
    private final Integer editingId;
    private final int backPage;
    private ItemStack chosen;
    private long price;
    private boolean used;

    private ItemEditorMenu(ItemStack chosen, long price, Integer editingId, int backPage) {
        this.chosen = chosen == null ? null : chosen.clone();
        this.price = price;
        this.editingId = editingId;
        this.backPage = backPage;
        this.inventory = Bukkit.createInventory(this, 54,
                Component.text(editingId == null ? "Adicionar item" : "Editar item"));
        render();
    }

    /** Abre o editor para um item novo. */
    public static void openNew(Player player, int backPage) {
        player.openInventory(new ItemEditorMenu(null, 0, null, backPage).getInventory());
    }

    /** Abre o editor com o item e o preço já preenchidos. */
    public static void openEdit(Player player, ShopItem shopItem, int backPage) {
        player.openInventory(new ItemEditorMenu(shopItem.item(), shopItem.price(), shopItem.id(), backPage).getInventory());
    }

    Integer editingId() {
        return editingId;
    }

    int backPage() {
        return backPage;
    }

    /** Item escolhido (cópia), ou null. */
    ItemStack chosen() {
        return chosen == null ? null : chosen.clone();
    }

    long price() {
        return price;
    }

    boolean used() {
        return used;
    }

    void markUsed() {
        used = true;
    }

    /** Escolhe o item (copia; o original continua no inventário do jogador). */
    void choose(ItemStack item) {
        chosen = item.clone();
        render();
    }

    /** Soma (ou subtrai) ao preço, sem passar de 0 nem de {@link ShopManager#MAX_PRICE}. */
    void addToPrice(long delta) {
        price = PriceButtons.clamp(price, delta);
        render();
    }

    void resetPrice() {
        price = 0;
        render();
    }

    private void render() {
        inventory.clear();
        inventory.setItem(ITEM_SLOT, chosen != null ? chosen.clone()
                : ShopUi.button(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§7Clique em um item do seu inventário"));
        PriceButtons.place(inventory, FIRST_PRICE_SLOT);
        inventory.setItem(PRICE_NAME_SLOT, ShopUi.button(Material.NAME_TAG,
                "Preço: §a" + EconomyManager.format(price)));
        inventory.setItem(RESET_SLOT, ShopUi.button(Material.BARRIER, "§cZerar preço"));
        inventory.setItem(CANCEL_SLOT, ShopUi.button(Material.RED_CONCRETE, "§cCancelar"));
        inventory.setItem(SAVE_SLOT, ShopUi.button(Material.LIME_CONCRETE, "§aSalvar"));
        if (editingId != null) {
            inventory.setItem(REMOVE_SLOT, ShopUi.button(Material.LAVA_BUCKET, "§4Remover da loja"));
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
