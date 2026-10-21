package br.com.murilo.muriloplug.shop;

import br.com.murilo.muriloplug.economy.EconomyManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pedaços de interface compartilhados pelos menus da loja. */
public final class ShopUi {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private ShopUi() {
    }

    /** Texto com códigos de cor §, sem o itálico padrão de nome/lore de item. */
    public static Component text(String legacy) {
        return LEGACY.deserialize(legacy).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static ItemStack button(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text(name));
        if (lore.length > 0) {
            List<Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(text(line));
            }
            meta.lore(lines);
        }
        item.setItemMeta(meta);
        return item;
    }

    /** Vidro cinza sem nome, para preencher espaços da barra. */
    public static ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setHideTooltip(true);
        item.setItemMeta(meta);
        return item;
    }

    /** Cópia do item da loja com as linhas extras de preço e ação (o item salvo não é alterado). */
    static ItemStack display(ShopItem shopItem, boolean admin) {
        ItemStack item = shopItem.item();
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(text("Preço: §a" + EconomyManager.format(shopItem.price())));
        lore.add(text(admin ? "§eClique para editar" : "Clique para comprar"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** Número com separador de milhar, sem o cifrão: 1.250. */
    public static String number(long value) {
        return String.format(Locale.of("pt", "BR"), "%,d", value);
    }
}
