package br.com.murilo.muriloplug.classes.engenheiro;

import br.com.murilo.muriloplug.shop.ShopUi;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/** Confirmação verde/vermelho "Colocar estrutura aqui?". */
final class PlaceConfirmMenu implements EngenheiroHolder {

    static final int CANCEL_SLOT = 3;
    static final int INFO_SLOT = 4;
    static final int CONFIRM_SLOT = 5;

    private final Inventory inventory;
    /** Vira true quando o jogador clica em um dos botões (fechar com ESC não conta). */
    private boolean used;

    private PlaceConfirmMenu(String sizeText, int blocks) {
        this.inventory = Bukkit.createInventory(this, 9, Component.text("Colocar estrutura aqui?"));
        inventory.setItem(CANCEL_SLOT, ShopUi.button(Material.RED_CONCRETE, "§cCancelar prévia",
                "§7O livro continua cheio."));
        inventory.setItem(INFO_SLOT, ShopUi.button(Material.BOOK, "§eColocar estrutura",
                "§7Tamanho: " + sizeText, "§7Blocos: " + blocks, "§cO livro será gasto."));
        inventory.setItem(CONFIRM_SLOT, ShopUi.button(Material.LIME_CONCRETE, "§aColocar"));
    }

    static void open(Player player, String sizeText, int blocks) {
        player.openInventory(new PlaceConfirmMenu(sizeText, blocks).getInventory());
    }

    boolean used() {
        return used;
    }

    void markUsed() {
        used = true;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
