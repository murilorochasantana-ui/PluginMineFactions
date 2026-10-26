package br.com.murilo.muriloplug.maquinas;

import br.com.murilo.muriloplug.shop.ShopUi;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Ícones dos menus: nome e lore com códigos §, aceitando também Components (nomes de item traduzidos) e listas. */
final class Icone {

    private Icone() {
    }

    /** Cada linha da lore pode ser String, Component ou uma coleção deles. */
    static ItemStack de(Material material, String nome, Object... lore) {
        return com(new ItemStack(material), nome, lore);
    }

    /** Põe nome (null = mantém o do item) e lore no item (que é alterado e devolvido). */
    static ItemStack com(ItemStack item, String nome, Object... lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        if (nome != null) {
            meta.displayName(ShopUi.text(nome));
        }
        List<Component> linhas = new ArrayList<>();
        for (Object linha : lore) {
            adicionar(linhas, linha);
        }
        if (!linhas.isEmpty()) {
            meta.lore(linhas);
        }
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    /** Acrescenta linhas à lore que o item já tem. */
    static ItemStack mais(ItemStack item, Object... lore) {
        ItemMeta meta = item.getItemMeta();
        List<Component> linhas = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        for (Object linha : lore) {
            adicionar(linhas, linha);
        }
        meta.lore(linhas);
        item.setItemMeta(meta);
        return item;
    }

    private static void adicionar(List<Component> linhas, Object linha) {
        if (linha instanceof Component component) {
            linhas.add(component);
        } else if (linha instanceof Collection<?> colecao) {
            colecao.forEach(item -> adicionar(linhas, item));
        } else if (linha != null) {
            linhas.add(ShopUi.text(String.valueOf(linha)));
        }
    }

    /** Barra "■■■■■□□□□□" (10 partes). */
    static String barra(double atual, double maximo) {
        int cheias = maximo <= 0 ? 0 : (int) Math.round(Math.max(0, Math.min(1, atual / maximo)) * 10);
        return "§a" + "■".repeat(cheias) + "§7" + "■".repeat(10 - cheias);
    }
}
