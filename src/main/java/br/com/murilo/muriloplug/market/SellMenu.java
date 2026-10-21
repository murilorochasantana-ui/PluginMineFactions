package br.com.murilo.muriloplug.market;

import br.com.murilo.muriloplug.economy.EconomyManager;
import br.com.murilo.muriloplug.shop.PriceButtons;
import br.com.murilo.muriloplug.shop.ShopUi;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Editor de anúncio (54 slots), para vender um item novo ou mudar o preço de um anúncio seu.
 * O estado fica aqui no holder: item escolhido e o slot dele no inventário, preço, qual anúncio está sendo editado
 * (null = anúncio novo) e a página do mercado para onde voltar.
 */
public final class SellMenu implements MarketHolder {

    static final int HEAD_SLOT = 4;
    static final int ITEM_SLOT = 13;
    static final int FIRST_PRICE_SLOT = PriceButtons.FIRST_SLOT;
    static final int PRICE_NAME_SLOT = 31;
    static final int RESET_SLOT = 40;
    static final int CANCEL_SLOT = 45;
    static final int REMOVE_SLOT = 49;
    static final int CONFIRM_SLOT = 53;

    private final Inventory inventory;
    private final UUID seller;
    private final String sellerName;
    private final Long editingId;
    private final int backPage;
    private ItemStack chosen;
    /** Slot do inventário do jogador de onde o item foi escolhido (-1 = nenhum). */
    private int chosenSlot = -1;
    private long price;
    private boolean used;

    private SellMenu(Player player, ItemStack chosen, long price, Long editingId, int backPage) {
        this.seller = player.getUniqueId();
        this.sellerName = player.getName();
        this.chosen = chosen == null ? null : chosen.clone();
        this.price = price;
        this.editingId = editingId;
        this.backPage = backPage;
        this.inventory = Bukkit.createInventory(this, 54,
                Component.text(editingId == null ? "Vender item" : "Editar anúncio"));
        render();
    }

    /** Abre o editor para um anúncio novo. */
    static void openNew(Player player, int backPage) {
        player.openInventory(new SellMenu(player, null, 0, null, backPage).getInventory());
    }

    /** Abre o editor de um anúncio existente: o item é fixo e o preço já vem preenchido. */
    static void openEdit(Player player, Listing listing, int backPage) {
        player.openInventory(new SellMenu(player, listing.item(), listing.price(), listing.id(), backPage).getInventory());
    }

    Long editingId() {
        return editingId;
    }

    boolean editing() {
        return editingId != null;
    }

    int backPage() {
        return backPage;
    }

    /** Item escolhido (cópia), ou null. */
    ItemStack chosen() {
        return chosen == null ? null : chosen.clone();
    }

    int chosenSlot() {
        return chosenSlot;
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

    /** Escolhe o item: guarda uma CÓPIA e o slot de onde veio. O item continua no inventário do jogador. */
    void choose(ItemStack item, int inventorySlot) {
        chosen = item.clone();
        chosenSlot = inventorySlot;
        render();
    }

    void clearChosen() {
        chosen = null;
        chosenSlot = -1;
        render();
    }

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
        inventory.setItem(HEAD_SLOT, MarketUi.sellerHead(seller, sellerName));
        inventory.setItem(ITEM_SLOT, chosen != null ? chosen.clone()
                : ShopUi.button(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§7Clique em um item do seu inventário"));
        PriceButtons.place(inventory, FIRST_PRICE_SLOT);
        inventory.setItem(PRICE_NAME_SLOT, ShopUi.button(Material.NAME_TAG, "Preço: §a" + EconomyManager.format(price)));
        inventory.setItem(RESET_SLOT, ShopUi.button(Material.BARRIER, "§cZerar preço"));
        inventory.setItem(CANCEL_SLOT, ShopUi.button(Material.RED_CONCRETE, "§cCancelar"));
        inventory.setItem(CONFIRM_SLOT, ShopUi.button(Material.LIME_CONCRETE, editing() ? "§aSalvar" : "§aAnunciar"));
        if (editing()) {
            inventory.setItem(REMOVE_SLOT, ShopUi.button(Material.LAVA_BUCKET, "§4Remover anúncio"));
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
