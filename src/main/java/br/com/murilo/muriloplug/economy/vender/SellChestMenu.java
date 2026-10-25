package br.com.murilo.muriloplug.economy.vender;

import br.com.murilo.muriloplug.economy.EconomyManager;
import br.com.murilo.muriloplug.shop.ShopUi;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.Map;

/**
 * "Vender ao Servidor" (54 slots). As linhas 1 a 5 (slots 0 a 44) são livres: o jogador põe e tira itens. A linha de
 * baixo é a barra (ajuda, botão Vender com o total, vidro cinza) e nunca pode ser mexida. A confirmação aparece na
 * própria barra, então os itens nunca saem deste inventário até serem vendidos ou devolvidos.
 */
public final class SellChestMenu implements InventoryHolder {

    static final int AREA_SIZE = 45;
    static final int HELP_SLOT = 45;
    static final int SELL_SLOT = 49;
    static final int CANCEL_SLOT = 48;
    static final int CONFIRM_SLOT = 50;

    /** O que está à venda agora: unidades por material, quanto rende e quantas pilhas não podem ser vendidas. */
    record Summary(Map<Material, Integer> units, int totalUnits, long payout, int refusedStacks) {
    }

    private final Inventory inventory;
    private final SellPrices prices;
    private boolean confirming;
    /** Os itens já foram devolvidos (fechou o menu ou o servidor desligou): nada mais pode sair daqui. */
    private boolean finished;

    SellChestMenu(SellPrices prices) {
        this.prices = prices;
        this.inventory = Bukkit.createInventory(this, 54, Component.text("Vender ao Servidor"));
        render();
    }

    boolean confirming() {
        return confirming;
    }

    boolean finished() {
        return finished;
    }

    void setConfirming(boolean confirming) {
        this.confirming = confirming;
        render();
    }

    /** O que pode ser vendido nos slots 0 a 44, com o preço de agora. */
    Summary summary() {
        Map<Material, Integer> units = new EnumMap<>(Material.class);
        int refused = 0;
        for (int slot = 0; slot < AREA_SIZE; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (prices.refusal(item) != null) {
                refused++;
            } else {
                units.merge(item.getType(), item.getAmount(), Integer::sum);
            }
        }
        double total = 0;
        int totalUnits = 0;
        for (Map.Entry<Material, Integer> entry : units.entrySet()) {
            total += prices.quote(entry.getKey(), entry.getValue());
            totalUnits += entry.getValue();
        }
        return new Summary(units, totalUnits, new SellPrices.Quote(totalUnits, total).payout(), refused);
    }

    /** Redesenha só a barra de baixo (os itens do jogador não são tocados). */
    void render() {
        for (int slot = AREA_SIZE; slot < 54; slot++) {
            inventory.setItem(slot, ShopUi.filler());
        }
        Summary summary = summary();
        String refused = summary.refusedStacks() > 0
                ? "§c" + summary.refusedStacks() + " pilha(s) sem preço voltam ao fechar" : "§7Todos os itens têm preço";
        if (confirming) {
            inventory.setItem(CANCEL_SLOT, ShopUi.button(Material.RED_CONCRETE, "§cCancelar"));
            inventory.setItem(SELL_SLOT, ShopUi.button(Material.PAPER, "§eConfirmar a venda?",
                    "§7Itens: §f" + summary.totalUnits(), "§7Total: §a" + EconomyManager.format(summary.payout()), refused));
            inventory.setItem(CONFIRM_SLOT, ShopUi.button(Material.LIME_CONCRETE,
                    "§aConfirmar: " + summary.totalUnits() + " itens por " + EconomyManager.format(summary.payout())));
            return;
        }
        inventory.setItem(HELP_SLOT, ShopUi.button(Material.BOOK, "§eComo vender",
                "§7Coloque os itens nas 5 linhas de cima.",
                "§7Só itens da tabela (/vender precos) são vendidos.",
                "§7Itens com nome, encantados ou especiais não.",
                "§7O preço cai quando muita gente vende o mesmo",
                "§7item e volta a subir com o tempo.",
                "§7Fechar o baú devolve todos os itens."));
        inventory.setItem(SELL_SLOT, ShopUi.button(Material.LIME_CONCRETE,
                "§aVender §7(" + summary.totalUnits() + " itens: §a" + EconomyManager.format(summary.payout()) + "§7)",
                refused, "§7Pago em notas de dinheiro."));
    }

    /**
     * Tira do menu os itens que podem ser vendidos e devolve o resumo deles (calculado com o preço de agora). Os
     * itens sem preço continuam no menu.
     */
    Summary takeSellable() {
        Summary summary = summary();
        for (int slot = 0; slot < AREA_SIZE; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir() && prices.refusal(item) == null) {
                inventory.setItem(slot, null);
            }
        }
        return summary;
    }

    /** Devolve todos os itens das linhas 1 a 5 ao jogador (o que não couber cai no chão). Só uma vez. */
    void giveBack(Player player) {
        if (finished) {
            return;
        }
        finished = true;
        for (int slot = 0; slot < AREA_SIZE; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            inventory.setItem(slot, null);
            player.getInventory().addItem(item).values()
                    .forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
