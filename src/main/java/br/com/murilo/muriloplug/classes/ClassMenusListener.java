package br.com.murilo.muriloplug.classes;

import br.com.murilo.muriloplug.combat.CombatManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/** Cliques dos menus de classe. Todo clique e arrasto é cancelado: nada pode ser pego, movido ou colocado. */
public final class ClassMenusListener implements Listener {

    private final JavaPlugin plugin;
    private final ClassManager classes;
    private final CombatManager combat;

    public ClassMenusListener(JavaPlugin plugin, ClassManager classes, CombatManager combat) {
        this.plugin = plugin;
        this.classes = classes;
        this.combat = combat;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof ClassHolder)) {
            return;
        }
        // Cancela tudo (shift-click, teclas 1-9, tecla F, clique duplo, cliques no inventário do jogador).
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        if (holder instanceof ClassMenu menu) {
            onMenuClick(player, menu.classAt(event.getSlot()));
        } else if (holder instanceof ClassConfirmMenu confirm) {
            switch (event.getSlot()) {
                case ClassConfirmMenu.CANCEL_SLOT -> {
                    player.sendMessage("§eNada mudou.");
                    closeLater(player);
                }
                case ClassConfirmMenu.CONFIRM_SLOT -> onConfirm(player, confirm);
                default -> { }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ClassHolder) {
            event.setCancelled(true);
        }
    }

    private void onMenuClick(Player player, PlayerClass clicked) {
        if (clicked == null) {
            return;
        }
        if (!clicked.available()) {
            player.sendMessage("§8Esta classe ainda não está disponível.");
        } else if (classes.getClass(player.getUniqueId()) != null) {
            player.sendMessage("§cUse /classe trocar para mudar de classe.");
        } else {
            // Abre a confirmação no tick seguinte, fora do evento de clique.
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> ClassConfirmMenu.open(player, ClassConfirmMenu.Kind.CHOOSE, clicked));
        }
    }

    private void onConfirm(Player player, ClassConfirmMenu confirm) {
        if (confirm.used()) {
            return;
        }
        confirm.markUsed();
        UUID id = player.getUniqueId();
        PlayerClass target = confirm.target();

        // Revalida tudo: o estado pode ter mudado com o menu aberto.
        if (combat.isInCombat(player)) {
            player.sendMessage("§cVocê não pode mudar de classe em combate.");
            closeLater(player);
            return;
        }
        PlayerClass current = classes.getClass(id);
        if (confirm.kind() == ClassConfirmMenu.Kind.CHOOSE) {
            if (current != null) {
                player.sendMessage("§cUse /classe trocar para mudar de classe.");
            } else if (!target.available()) {
                player.sendMessage("§cEsta classe ainda não está disponível.");
            } else {
                classes.setClass(id, target);
                player.sendMessage("§aVocê agora é " + target.coloredName() + "§a!");
            }
            closeLater(player);
        } else {
            if (current != target) {
                player.sendMessage("§cA sua classe mudou. Use /classe trocar de novo.");
                closeLater(player);
                return;
            }
            classes.removeClass(id);
            player.sendMessage("§eVocê deixou de ser " + target.coloredName() + "§e. Escolha uma nova classe.");
            plugin.getServer().getScheduler().runTask(plugin, () -> ClassMenu.open(player, classes));
        }
    }

    private void closeLater(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> player.closeInventory());
    }
}
