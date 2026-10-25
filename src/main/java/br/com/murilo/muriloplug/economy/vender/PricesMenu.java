package br.com.murilo.muriloplug.economy.vender;

import br.com.murilo.muriloplug.shop.ShopUi;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /vender precos: menu só de leitura com o preço ATUAL de cada item da tabela e a tendência. */
public final class PricesMenu implements InventoryHolder {

    static final int PER_PAGE = 45;
    static final int PREVIOUS_SLOT = 45;
    static final int INFO_SLOT = 49;
    static final int NEXT_SLOT = 53;

    private final Inventory inventory;
    private final SellPrices prices;
    private int page;

    private PricesMenu(SellPrices prices) {
        this.prices = prices;
        this.inventory = Bukkit.createInventory(this, 54, Component.text("Preços de venda"));
        render();
    }

    public static void open(Player player, SellPrices prices) {
        player.openInventory(new PricesMenu(prices).getInventory());
    }

    int page() {
        return page;
    }

    int pageCount() {
        return Math.max(1, (prices.materials().size() + PER_PAGE - 1) / PER_PAGE);
    }

    void changePage(int delta) {
        page = Math.max(0, Math.min(pageCount() - 1, page + delta));
        render();
    }

    private void render() {
        inventory.clear();
        List<Material> materials = prices.materials();
        int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < materials.size(); i++) {
            inventory.setItem(i, display(materials.get(start + i)));
        }
        for (int slot = PER_PAGE; slot < 54; slot++) {
            inventory.setItem(slot, ShopUi.filler());
        }
        if (page > 0) {
            inventory.setItem(PREVIOUS_SLOT, ShopUi.button(Material.ARROW, "§ePágina anterior"));
        }
        if (page < pageCount() - 1) {
            inventory.setItem(NEXT_SLOT, ShopUi.button(Material.ARROW, "§ePróxima página"));
        }
        inventory.setItem(INFO_SLOT, ShopUi.button(Material.BOOK, "§eComo funciona",
                "§7Preço atual = preço base × multiplicador.",
                "§7Cada unidade vendida baixa o preço um pouco;",
                "§7ele volta a subir sozinho a cada hora.",
                "§a↑ §7subindo  §c↓ §7caindo  §f= §7estável",
                "§7Use /vender para vender."));
    }

    private ItemStack display(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>();
        int trend = prices.trend(material);
        String arrow = trend < 0 ? "§c↓" : trend > 0 ? "§a↑" : "§f=";
        lore.add(ShopUi.text("§7Preço atual: §a" + SellPrices.formatPrice(prices.currentPrice(material)) + " " + arrow));
        lore.add(ShopUi.text("§7Preço base: §f" + SellPrices.formatPrice(prices.basePrice(material))));
        lore.add(ShopUi.text("§7Multiplicador: §f"
                + String.format(Locale.ROOT, "%.0f%%", prices.multiplier(material) * 100)));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
