package br.com.murilo.muriloplug.maquinas;

import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Menu de baú das máquinas. Todo clique e arrasto com ele aberto é cancelado pelo {@link MaquinasMenusListener}; os
 * botões rodam a ação do slot. O conteúdo é desenhado por uma função ({@link #desenho}), chamada de novo a cada
 * {@link #redesenhar()}: os dados ficam fora do inventário (na máquina, na config), então redesenhar nunca perde nada.
 */
final class Menu implements InventoryHolder {

    /** Ação de um clique (o evento já vem cancelado). */
    interface Clique {
        void run(Player player, InventoryClickEvent event);
    }

    private final Inventory inventory;
    private final Map<Integer, Clique> botoes = new HashMap<>();
    private Consumer<Menu> desenho = menu -> { };
    /** Clique no inventário do próprio jogador (null = ignorado). */
    Clique inferior;
    /** Chamado ao fechar (devolver itens). */
    Consumer<Player> aoFechar;
    /** Id da máquina mostrada (os menus dela são redesenhados quando ela muda). */
    String maquinaId;
    /** Menus do painel do admin: só abrem e funcionam com a permissão. */
    boolean painel;
    /** Clique simples no próprio inventário continua normal (para pegar itens no cursor e usar nos botões). */
    boolean inventarioLivre;

    Menu(int linhas, String titulo) {
        this.inventory = Bukkit.createInventory(this, linhas * 9, ShopUi.text(titulo));
    }

    Menu desenho(Consumer<Menu> desenho) {
        this.desenho = desenho;
        return this;
    }

    void redesenhar() {
        inventory.clear();
        botoes.clear();
        desenho.accept(this);
    }

    void abrir(Player player) {
        redesenhar();
        player.openInventory(inventory);
    }

    void set(int slot, ItemStack item, Clique clique) {
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, item);
        if (clique != null) {
            botoes.put(slot, clique);
        } else {
            botoes.remove(slot);
        }
    }

    void set(int slot, ItemStack item) {
        set(slot, item, null);
    }

    /** Vidro cinza nos slots vazios de {@code de} até {@code ate} (inclusive). */
    void preencher(int de, int ate) {
        for (int slot = de; slot <= ate && slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, ShopUi.filler());
            }
        }
    }

    Clique botao(int slot) {
        return botoes.get(slot);
    }

    int tamanho() {
        return inventory.getSize();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
