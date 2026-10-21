package br.com.murilo.muriloplug.classes.engenheiro;

import br.com.murilo.muriloplug.shop.ShopUi;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/** Menu "Construção" (27 slots): salvar, resumo e cancelar. */
final class BuildMenu implements EngenheiroHolder {

    static final int SAVE_SLOT = 11;
    static final int INFO_SLOT = 13;
    static final int CANCEL_SLOT = 15;

    private final Inventory inventory;

    private BuildMenu(BuildManager.Scan scan) {
        this.inventory = Bukkit.createInventory(this, 27, Component.text("Construção"));
        inventory.setItem(SAVE_SLOT, ShopUi.button(Material.LIME_CONCRETE, "§aSalvar estrutura",
                "§7Guarda a construção no livro e", "§7apaga os blocos do mundo."));
        inventory.setItem(INFO_SLOT, ShopUi.button(Material.BOOK, "§eResumo da construção",
                "§7Tamanho usado: " + scan.sizeText(), "§7Blocos: " + scan.count()));
        inventory.setItem(CANCEL_SLOT, ShopUi.button(Material.RED_CONCRETE, "§cCancelar estrutura",
                "§7Os blocos ficam no mundo e o", "§7chão volta ao original."));
    }

    static void open(Player player, BuildManager manager, OpenBuild build) {
        player.openInventory(new BuildMenu(manager.scan(build)).getInventory());
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
