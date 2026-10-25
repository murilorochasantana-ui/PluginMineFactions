package br.com.murilo.muriloplug.eventos;

import br.com.murilo.muriloplug.economy.EconomyManager;
import br.com.murilo.muriloplug.shop.ShopUi;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Editor da tabela do Airdrop (54 slots), no estilo do editor da loja: clique num item do SEU inventário para
 * adicionar uma cópia; clique num item da tabela para escolhê-lo e use os botões de chance (+1%/+10%/-1%/-10%) ou
 * remover. O botão de dinheiro adiciona um Maço de Dinheiro como item. Todo clique é cancelado: nada sai do menu.
 */
public final class AirdropEditorMenu implements InventoryHolder {

    static final int PER_PAGE = 45;
    static final int PREVIOUS_SLOT = 45;
    static final int MINUS_TEN_SLOT = 46;
    static final int MINUS_ONE_SLOT = 47;
    static final int PLUS_ONE_SLOT = 48;
    static final int PLUS_TEN_SLOT = 49;
    static final int REMOVE_SLOT = 50;
    static final int NOTE_SLOT = 51;
    static final int INFO_SLOT = 52;
    static final int NEXT_SLOT = 53;

    private final Inventory inventory;
    private final AirdropTable table;
    private int page;
    private int selected = -1;
    /** Valores do botão de dinheiro (o direito troca). */
    static final long[] CASH_VALUES = {100, 500, 1_000, 5_000, 10_000, 50_000};
    private int noteIndex = 2; // começa em $1.000

    AirdropEditorMenu(AirdropTable table) {
        this.table = table;
        this.inventory = Bukkit.createInventory(this, 54, Component.text("Airdrop (editor)"));
    }

    int page() {
        return page;
    }

    int selected() {
        return selected;
    }

    void select(int index) {
        selected = index;
    }

    int noteIndex() {
        return noteIndex;
    }

    void nextNote() {
        noteIndex = (noteIndex + 1) % CASH_VALUES.length;
    }

    long cashValue() {
        return CASH_VALUES[noteIndex];
    }

    int pageCount() {
        return Math.max(1, (table.size() + PER_PAGE - 1) / PER_PAGE);
    }

    void changePage(int delta) {
        page = Math.max(0, Math.min(pageCount() - 1, page + delta));
    }

    /** Índice da tabela no slot (ou -1). */
    int indexAt(int slot) {
        int index = page * PER_PAGE + slot;
        return slot >= 0 && slot < PER_PAGE && index < table.size() ? index : -1;
    }

    void render() {
        inventory.clear();
        page = Math.max(0, Math.min(pageCount() - 1, page));
        if (selected >= table.size()) {
            selected = -1;
        }
        for (int slot = 0; slot < PER_PAGE; slot++) {
            int index = indexAt(slot);
            if (index >= 0) {
                inventory.setItem(slot, display(table.get(index), index == selected));
            }
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
        String target = selected < 0 ? "§7(escolha um item da tabela)" : "§7Item escolhido: §f#" + (selected + 1);
        inventory.setItem(MINUS_TEN_SLOT, ShopUi.button(Material.RED_CONCRETE, "§c-10%", target));
        inventory.setItem(MINUS_ONE_SLOT, ShopUi.button(Material.RED_TERRACOTTA, "§c-1%", target));
        inventory.setItem(PLUS_ONE_SLOT, ShopUi.button(Material.LIME_TERRACOTTA, "§a+1%", target));
        inventory.setItem(PLUS_TEN_SLOT, ShopUi.button(Material.LIME_CONCRETE, "§a+10%", target));
        inventory.setItem(REMOVE_SLOT, ShopUi.button(Material.LAVA_BUCKET, "§4Remover da tabela", target));
        inventory.setItem(NOTE_SLOT, ShopUi.button(Material.PAPER, "§aAdicionar maço de " + EconomyManager.format(cashValue()),
                "§7Esquerdo: adiciona o maço na tabela", "§7Direito: troca o valor"));
        inventory.setItem(INFO_SLOT, ShopUi.button(Material.BOOK, "§eComo editar",
                "§7Clique num item do seu inventário: entra",
                "§7na tabela com 10% de chance.",
                "§7Clique num item da tabela para escolher e",
                "§7ajuste a chance com os botões.",
                "§7O baú sorteia de 4 a 8 itens.",
                "§7Itens na tabela: §f" + table.size()));
    }

    private static ItemStack display(AirdropTable.Entry entry, boolean selected) {
        ItemStack item = entry.item();
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(ShopUi.text("§eChance: §f" + entry.chance() + "%"));
        lore.add(ShopUi.text(selected ? "§a§l✔ Escolhido" : "§7Clique para escolher"));
        meta.lore(lore);
        if (selected) {
            meta.setEnchantmentGlintOverride(true);
        }
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
