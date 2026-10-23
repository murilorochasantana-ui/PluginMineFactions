package br.com.murilo.muriloplug.classes.engenheiro;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

/**
 * Menu de um projeto (54 slots): materiais nos 45 primeiros slots; na última linha, voltar, o Livro de Estrutura
 * vazio, criar livro, renomear e apagar. Quem monta o conteúdo é o {@link PlantaManager}.
 */
final class PlantaProjectMenu implements EngenheiroHolder {

    static final int MATERIAL_SLOTS = 45;
    static final int BACK_SLOT = 45;
    static final int BOOK_SLOT = 47;
    static final int CREATE_SLOT = 49;
    static final int RENAME_SLOT = 51;
    static final int DELETE_SLOT = 53;

    private final Inventory inventory;
    private final UUID planta;
    private final String projectId;

    PlantaProjectMenu(UUID planta, String projectId, String name) {
        this.planta = planta;
        this.projectId = projectId;
        this.inventory = Bukkit.createInventory(this, 54, Component.text("Projeto: " + name));
    }

    UUID planta() {
        return planta;
    }

    String projectId() {
        return projectId;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
