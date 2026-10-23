package br.com.murilo.muriloplug.classes.engenheiro;

import br.com.murilo.muriloplug.shop.ShopUi;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** Menu "Planta de Engenharia" (54 slots): um papel de mapa por projeto. */
final class PlantaMenu implements EngenheiroHolder {

    static final int FIRST_SLOT = 18;
    static final int INFO_SLOT = 4;

    private final Inventory inventory;
    private final UUID planta;
    private final List<PlantaStore.Project> projects;

    PlantaMenu(UUID planta, List<PlantaStore.Project> projects, int max) {
        this.planta = planta;
        this.projects = projects;
        this.inventory = Bukkit.createInventory(this, 54, Component.text("Planta de Engenharia"));
        for (int slot = 0; slot < 54; slot++) {
            inventory.setItem(slot, ShopUi.filler());
        }
        inventory.setItem(INFO_SLOT, ShopUi.button(Material.BOOK, "§eProjetos: " + projects.size() + "/" + max,
                "§7Clique em um projeto para ver os", "§7materiais e criar um Livro de Estrutura."));
        for (int i = 0; i < 9; i++) {
            inventory.setItem(FIRST_SLOT + i, i < projects.size() ? icon(projects.get(i))
                    : ShopUi.button(Material.LIGHT_GRAY_STAINED_GLASS_PANE, i < max ? "§7Vazio" : "§8Indisponível"));
        }
    }

    private static ItemStack icon(PlantaStore.Project project) {
        ItemStack item = ShopUi.button(Material.PAPER, "§9" + project.name(),
                "§7Tamanho: " + project.size(),
                "§7Blocos: " + project.blocks(),
                "§7Autor: " + project.author(),
                "§7Data: " + new SimpleDateFormat("dd/MM/yyyy").format(new Date(project.date())),
                "§eClique para abrir");
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(NamespacedKey.minecraft("map"));
        item.setItemMeta(meta);
        return item;
    }

    UUID planta() {
        return planta;
    }

    /** Projeto do slot clicado, ou null. */
    PlantaStore.Project projectAt(int slot) {
        int index = slot - FIRST_SLOT;
        return index >= 0 && index < projects.size() ? projects.get(index) : null;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
