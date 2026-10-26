package br.com.murilo.muriloplug.maquinas;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Proteção de todos os menus das máquinas: qualquer clique e arrasto com um {@link Menu} aberto é cancelado (nada
 * entra nem sai sozinho); só os botões e o clique no próprio inventário (quando o menu aceita) fazem algo.
 */
public final class MaquinasMenusListener implements Listener {

    static final String ADMIN_PERMISSION = "mineplug.maquinas.admin";

    private final JavaPlugin plugin;

    public MaquinasMenusListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Menu menu) || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory clicked = event.getClickedInventory();
        ClickType click = event.getClick();
        if (menu.inventarioLivre && clicked != null && clicked != top
                && (click == ClickType.LEFT || click == ClickType.RIGHT)) {
            return; // mexer no próprio inventário (pegar combustível/upgrade no cursor) continua normal
        }
        event.setCancelled(true);
        if (menu.painel && !player.hasPermission(ADMIN_PERMISSION)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> player.closeInventory());
            return;
        }
        if (click != ClickType.LEFT && click != ClickType.RIGHT && click != ClickType.SHIFT_LEFT
                && click != ClickType.SHIFT_RIGHT) {
            return; // teclas 1-9, Q, F, clique duplo etc. são só cancelados
        }
        if (clicked == null) {
            return;
        }
        Menu.Clique acao = clicked == top ? menu.botao(event.getSlot()) : menu.inferior;
        if (acao != null) {
            acao.run(player, event);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof Menu menu
                && (!menu.inventarioLivre || event.getRawSlots().stream().anyMatch(slot -> slot < top.getSize()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Menu menu && menu.aoFechar != null
                && event.getPlayer() instanceof Player player) {
            var acao = menu.aoFechar;
            menu.aoFechar = null; // devolve uma vez só
            acao.accept(player);
        }
    }
}
