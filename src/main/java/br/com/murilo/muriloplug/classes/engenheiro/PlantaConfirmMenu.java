package br.com.murilo.muriloplug.classes.engenheiro;

import br.com.murilo.muriloplug.shop.ShopUi;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

/** Confirmação verde/vermelho de criar o livro ou de apagar o projeto. */
final class PlantaConfirmMenu implements EngenheiroHolder {

    enum Kind { CREATE, DELETE }

    static final int BACK_SLOT = 3;
    static final int INFO_SLOT = 4;
    static final int CONFIRM_SLOT = 5;

    private final Inventory inventory;
    private final Kind kind;
    private final UUID planta;
    private final String projectId;
    /** Vira true na primeira confirmação, para um clique duplo não repetir a ação. */
    private boolean used;

    PlantaConfirmMenu(Kind kind, UUID planta, String projectId, String name) {
        this.kind = kind;
        this.planta = planta;
        this.projectId = projectId;
        boolean create = kind == Kind.CREATE;
        this.inventory = Bukkit.createInventory(this, 9, Component.text(create ? "Criar livro?" : "Apagar projeto?"));
        inventory.setItem(BACK_SLOT, ShopUi.button(Material.RED_CONCRETE, "§cVoltar"));
        inventory.setItem(INFO_SLOT, create
                ? ShopUi.button(Material.BOOK, "§eCriar livro: " + name,
                        "§7Gasta os materiais e o Livro de", "§7Estrutura vazio. O projeto continua na Planta.")
                : ShopUi.button(Material.LAVA_BUCKET, "§4Apagar: " + name,
                        "§7O projeto some da Planta para sempre."));
        inventory.setItem(CONFIRM_SLOT, ShopUi.button(Material.LIME_CONCRETE, "§aConfirmar"));
    }

    Kind kind() {
        return kind;
    }

    UUID planta() {
        return planta;
    }

    String projectId() {
        return projectId;
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
