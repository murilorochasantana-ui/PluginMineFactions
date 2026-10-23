package br.com.murilo.muriloplug.classes.engenheiro;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

/** Cliques dos menus do Engenheiro. Todo clique e arrasto é cancelado: nada pode ser pego, movido ou colocado. */
public final class EngenheiroMenusListener implements Listener {

    private final JavaPlugin plugin;
    private final BuildManager builds;
    private final PlacementManager placement;
    private final PlantaManager plantas;

    public EngenheiroMenusListener(JavaPlugin plugin, BuildManager builds, PlacementManager placement,
                                   PlantaManager plantas) {
        this.plantas = plantas;
        this.placement = placement;
        this.plugin = plugin;
        this.builds = builds;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof EngenheiroHolder)) {
            return;
        }
        // Cancela tudo (shift-click, teclas 1-9, tecla F, clique duplo, cliques no inventário do jogador).
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        if (holder instanceof BuildMenu) {
            switch (event.getSlot()) {
                case BuildMenu.SAVE_SLOT -> openConfirmLater(player, BuildConfirmMenu.Kind.SAVE);
                case BuildMenu.CANCEL_SLOT -> openConfirmLater(player, BuildConfirmMenu.Kind.CANCEL);
                case BuildMenu.PLANTA_SLOT -> {
                    if (builds.plantaAvailable(player)) {
                        openConfirmLater(player, BuildConfirmMenu.Kind.SAVE_PLANT);
                    } else {
                        player.sendMessage("§cVocê precisa de uma Planta com espaço no inventário.");
                    }
                }
                default -> { }
            }
        } else if (holder instanceof PlaceConfirmMenu menu) {
            switch (event.getSlot()) {
                case PlaceConfirmMenu.CANCEL_SLOT -> {
                    menu.markUsed();
                    placement.cancelPreview(player);
                    closeLater(player);
                }
                case PlaceConfirmMenu.CONFIRM_SLOT -> {
                    if (!menu.used()) {
                        menu.markUsed();
                        placement.confirm(player);
                        closeLater(player);
                    }
                }
                default -> { }
            }
        } else if (holder instanceof PlantaMenu menu) {
            plantas.onListClick(player, menu, event.getSlot());
        } else if (holder instanceof PlantaProjectMenu menu) {
            plantas.onProjectClick(player, menu, event.getSlot());
        } else if (holder instanceof PlantaConfirmMenu menu) {
            plantas.onConfirmClick(player, menu, event.getSlot());
        } else if (holder instanceof BuildConfirmMenu confirm) {
            switch (event.getSlot()) {
                case BuildConfirmMenu.BACK_SLOT -> closeLater(player);
                case BuildConfirmMenu.CONFIRM_SLOT -> onConfirm(player, confirm);
                default -> { }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof EngenheiroHolder) {
            event.setCancelled(true);
        }
    }

    /** Fechou o menu de colocar sem escolher (ESC): a prévia volta a seguir a mira. */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof PlaceConfirmMenu menu && !menu.used()
                && event.getPlayer() instanceof Player player) {
            placement.unfreeze(player);
        }
    }

    private void onConfirm(Player player, BuildConfirmMenu confirm) {
        if (confirm.used()) {
            return;
        }
        confirm.markUsed();
        if (confirm.kind() == BuildConfirmMenu.Kind.SAVE_PLANT) {
            if (builds.ofOwner(player.getUniqueId()) == null) {
                player.sendMessage("§cVocê não tem uma construção aberta.");
            } else {
                plantas.askProjectName(player);
            }
        } else if (confirm.kind() == BuildConfirmMenu.Kind.SAVE) {
            builds.save(player);
        } else if (builds.ofOwner(player.getUniqueId()) == null) {
            player.sendMessage("§cVocê não tem uma construção aberta.");
        } else {
            builds.cancel(player.getUniqueId(), "§eConstrução cancelada. Os blocos ficaram no lugar.");
        }
        closeLater(player);
    }

    private void openConfirmLater(Player player, BuildConfirmMenu.Kind kind) {
        // Abre a confirmação no tick seguinte, fora do evento de clique.
        plugin.getServer().getScheduler().runTask(plugin, () -> BuildConfirmMenu.open(player, kind));
    }

    private void closeLater(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> player.closeInventory());
    }
}
