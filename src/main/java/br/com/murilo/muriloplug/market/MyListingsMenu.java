package br.com.murilo.muriloplug.market;

import br.com.murilo.muriloplug.shop.ShopUi;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** "Meus anúncios": baú de 54 slots com os anúncios do jogador (45 por página). Clicar em um abre o editor. */
public final class MyListingsMenu implements MarketHolder {

    static final int LISTINGS_PER_PAGE = 45;
    static final int PREVIOUS_SLOT = 45;
    static final int BACK_SLOT = 49;
    static final int NEXT_SLOT = 53;

    private final Inventory inventory;
    private final MarketManager market;
    private final UUID viewer;
    /** Id do anúncio de cada slot da página atual (-1 = vazio). */
    private final long[] listingIds = new long[LISTINGS_PER_PAGE];
    private int page;

    private MyListingsMenu(Player player, MarketManager market) {
        this.viewer = player.getUniqueId();
        this.market = market;
        this.inventory = Bukkit.createInventory(this, 54, Component.text("Meus anúncios"));
        render();
    }

    static void open(Player player, MarketManager market) {
        player.openInventory(new MyListingsMenu(player, market).getInventory());
    }

    int page() {
        return page;
    }

    int pageCount() {
        return Math.max(1, (market.count(viewer) + LISTINGS_PER_PAGE - 1) / LISTINGS_PER_PAGE);
    }

    /** Muda de página no próprio baú, sem reabrir. */
    void changePage(int delta) {
        page += delta;
        render();
    }

    /** Id do anúncio no slot, ou -1 se o slot não tiver anúncio. */
    long listingIdAt(int slot) {
        return slot >= 0 && slot < LISTINGS_PER_PAGE ? listingIds[slot] : -1;
    }

    /** Redesenha o baú com os anúncios atuais do jogador. */
    void render() {
        List<Listing> mine = market.bySeller(viewer);
        page = Math.max(0, Math.min(page, pageCount() - 1));
        inventory.clear();
        Arrays.fill(listingIds, -1);

        int start = page * LISTINGS_PER_PAGE;
        for (int i = 0; i < LISTINGS_PER_PAGE && start + i < mine.size(); i++) {
            listingIds[i] = mine.get(start + i).id();
            inventory.setItem(i, MarketUi.display(mine.get(start + i), viewer));
        }

        for (int slot = LISTINGS_PER_PAGE; slot < 54; slot++) {
            inventory.setItem(slot, ShopUi.filler());
        }
        if (page > 0) {
            inventory.setItem(PREVIOUS_SLOT, ShopUi.button(Material.ARROW, "§ePágina anterior"));
        }
        if (page < pageCount() - 1) {
            inventory.setItem(NEXT_SLOT, ShopUi.button(Material.ARROW, "§ePróxima página"));
        }
        inventory.setItem(BACK_SLOT, ShopUi.button(Material.ARROW, "§eVoltar ao mercado"));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
