package br.com.murilo.muriloplug.classes.engenheiro;

import br.com.murilo.muriloplug.shop.ShopUi;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/** Confirmação verde/vermelho de salvar ou de cancelar a construção. */
final class BuildConfirmMenu implements EngenheiroHolder {

    enum Kind { SAVE, CANCEL, SAVE_PLANT }

    static final int BACK_SLOT = 3;
    static final int INFO_SLOT = 4;
    static final int CONFIRM_SLOT = 5;

    private final Inventory inventory;
    private final Kind kind;
    /** Vira true na primeira confirmação, para um clique duplo não repetir a ação. */
    private boolean used;

    private BuildConfirmMenu(Kind kind) {
        this.kind = kind;
        boolean save = kind == Kind.SAVE;
        boolean plant = kind == Kind.SAVE_PLANT;
        this.inventory = Bukkit.createInventory(this, 9,
                Component.text(plant ? "Salvar projeto?" : save ? "Salvar estrutura?" : "Cancelar estrutura?"));
        inventory.setItem(BACK_SLOT, ShopUi.button(Material.RED_CONCRETE, "§cVoltar"));
        inventory.setItem(INFO_SLOT, plant
                ? ShopUi.button(Material.BLUE_CONCRETE, "§9Salvar projeto na Planta",
                        "§7O projeto vai para a Planta e os blocos", "§7ficam no mundo. Depois, digite o nome.")
                : save
                ? ShopUi.button(Material.BOOK, "§eSalvar estrutura",
                        "§7Os blocos e o conteúdo dos baús vão", "§7para o livro e somem do mundo.")
                : ShopUi.button(Material.BOOK, "§eCancelar estrutura",
                        "§7Os blocos ficam no mundo e o", "§7chão volta ao original."));
        inventory.setItem(CONFIRM_SLOT, ShopUi.button(Material.LIME_CONCRETE, "§aConfirmar"));
    }

    static void open(Player player, Kind kind) {
        player.openInventory(new BuildConfirmMenu(kind).getInventory());
    }

    Kind kind() {
        return kind;
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
