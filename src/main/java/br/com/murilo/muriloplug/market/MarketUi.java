package br.com.murilo.muriloplug.market;

import br.com.murilo.muriloplug.economy.EconomyManager;
import br.com.murilo.muriloplug.shop.ShopUi;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Pedaços de interface compartilhados pelos menus do mercado. */
final class MarketUi {

    private MarketUi() {
    }

    /** Cópia do item do anúncio com as linhas extras (vendedor, preço, tempo e ação). */
    static ItemStack display(Listing listing, UUID viewer) {
        ItemStack item = listing.item();
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(ShopUi.text("§7Vendedor: §e" + listing.sellerName()));
        lore.add(ShopUi.text("§7Preço: §a" + EconomyManager.format(listing.price())));
        lore.add(ShopUi.text("§7Anunciado há: " + ago(listing.createdAt())));
        lore.add(ShopUi.text(listing.seller().equals(viewer)
                ? "§bSeu anúncio - clique para editar"
                : "§eClique para comprar"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** Cabeça do vendedor com o nome "Vendedor: <nome>". */
    static ItemStack sellerHead(UUID seller, String name) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(seller));
        meta.displayName(ShopUi.text("§eVendedor: " + name));
        head.setItemMeta(meta);
        return head;
    }

    /** Tempo desde o anúncio: "menos de 1 min", "5 min", "2 h", "3 dias". */
    static String ago(long createdAt) {
        long minutes = Math.max(0, System.currentTimeMillis() - createdAt) / 60_000;
        if (minutes < 1) {
            return "menos de 1 min";
        }
        if (minutes < 60) {
            return minutes + " min";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + " h";
        }
        long days = hours / 24;
        return days + (days == 1 ? " dia" : " dias");
    }

    /** Nome legível do item para mensagens ("3x " na frente quando há mais de um). */
    static Component itemLabel(ItemStack item) {
        String amount = item.getAmount() > 1 ? item.getAmount() + "x " : "";
        return ShopUi.text("§f" + amount).append(item.displayName());
    }
}
