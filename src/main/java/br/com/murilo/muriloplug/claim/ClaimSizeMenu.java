package br.com.murilo.muriloplug.claim;

import br.com.murilo.muriloplug.economy.EconomyManager;
import br.com.murilo.muriloplug.shop.ShopUi;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * /claim sem argumentos: baú "Novo Claim" (27 slots) com os tamanhos e quanto cada um soma na manutenção diária do
 * clan. Clicar num tamanho fecha o menu e faz o mesmo que /claim &lt;tamanho&gt;. Todo clique e arrasto é cancelado.
 */
public final class ClaimSizeMenu implements InventoryHolder {

    /** {custo de hoje, custo com a área extra} da manutenção do clan, ou null se o jogador não tiver clan. */
    @FunctionalInterface
    public interface UpkeepPreview {
        long[] preview(Player player, long extraArea);
    }

    private static final int INFO_SLOT = 4;
    private static final int FIRST_OPTION_SLOT = 11;

    private final Inventory inventory;
    private final Map<Integer, Integer> sizeAt = new HashMap<>();

    private ClaimSizeMenu(Player player, List<Integer> sizes, UpkeepPreview preview) {
        this.inventory = Bukkit.createInventory(this, 27, Component.text("Novo Claim"));
        for (int slot = 0; slot < 27; slot++) {
            inventory.setItem(slot, ShopUi.filler());
        }
        long[] today = preview.preview(player, 0);
        inventory.setItem(INFO_SLOT, today == null
                ? ShopUi.button(Material.BOOK, "§eManutenção dos claims",
                "§aVocê não tem clan: seus claims não pagam manutenção.")
                : ShopUi.button(Material.BOOK, "§eManutenção dos claims",
                "§7O clan já paga hoje: §e" + EconomyManager.format(today[0]) + "/dia",
                "§7Cada opção mostra quanto passa a pagar",
                "§7com o claim novo.", "§7Cobrada todo dia pelo Cofre do clan."));
        int slot = FIRST_OPTION_SLOT;
        int index = 0;
        for (int size : sizes) {
            if (size < 1 || size > Claim.MAX_SIZE || slot >= 27) {
                continue;
            }
            long area = (long) size * size;
            long[] cost = preview.preview(player, area);
            ItemStack item = cost == null
                    ? ShopUi.button(Material.GRASS_BLOCK, "§a" + size + "x" + size,
                    "§7Tamanho: §f" + size + "x" + size, "§7Área: §f" + area + " blocos",
                    "§aSem manutenção (você não tem clan)", "§eClique para escolher")
                    : ShopUi.button(Material.GRASS_BLOCK, "§a" + size + "x" + size,
                    "§7Tamanho: §f" + size + "x" + size, "§7Área: §f" + area + " blocos",
                    "§7Manutenção: §e+" + EconomyManager.format(cost[1] - cost[0]) + "/dia",
                    "§7O clan passaria a pagar: §e" + EconomyManager.format(cost[1]) + "/dia",
                    "§eClique para escolher");
            item.setAmount(Math.min(64, ++index)); // tamanhos maiores, pilhas maiores
            inventory.setItem(slot, item);
            sizeAt.put(slot, size);
            slot = slot == 15 ? 19 : slot + 1;
        }
    }

    public static void open(Player player, List<Integer> sizes, UpkeepPreview preview) {
        player.openInventory(new ClaimSizeMenu(player, sizes, preview).getInventory());
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** Cliques do menu: só o tamanho escolhido faz algo (fecha e roda /claim &lt;tamanho&gt;). */
    public static final class Clicks implements Listener {

        private final JavaPlugin plugin;

        public Clicks(JavaPlugin plugin) {
            this.plugin = plugin;
        }

        @EventHandler
        public void onDrag(InventoryDragEvent event) {
            if (event.getView().getTopInventory().getHolder() instanceof ClaimSizeMenu) {
                event.setCancelled(true);
            }
        }

        @EventHandler
        public void onClick(InventoryClickEvent event) {
            if (!(event.getView().getTopInventory().getHolder() instanceof ClaimSizeMenu menu)) {
                return;
            }
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)
                    || event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }
            Integer size = menu.sizeAt.get(event.getSlot());
            if (size == null) {
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.closeInventory();
                player.performCommand("claim " + size);
            });
        }
    }
}
