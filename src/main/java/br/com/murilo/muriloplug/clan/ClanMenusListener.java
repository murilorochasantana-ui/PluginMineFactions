package br.com.murilo.muriloplug.clan;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Cliques nos menus do clan. Todo clique e arrasto com um menu do clan aberto é cancelado; o menu de informações
 * é só de visualização, e o de confirmação só reage aos botões vermelho e verde.
 */
public final class ClanMenusListener implements Listener {

    private final JavaPlugin plugin;
    private final ClanManager clans;

    public ClanMenusListener(JavaPlugin plugin, ClanManager clans) {
        this.plugin = plugin;
        this.clans = clans;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ClanHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof ClanHolder)) {
            return;
        }
        event.setCancelled(true);

        if (!(holder instanceof ClanConfirmMenu menu) || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ClickType click = event.getClick();
        if ((!click.isLeftClick() && !click.isRightClick()) || event.getClickedInventory() != top) {
            return;
        }

        if (event.getSlot() == ClanConfirmMenu.CANCEL_SLOT) {
            player.sendMessage("§eNada foi alterado.");
            closeLater(player);
        } else if (event.getSlot() == ClanConfirmMenu.CONFIRM_SLOT && !menu.used()) {
            menu.markUsed();
            execute(player, menu);
            closeLater(player);
        }
    }

    private void execute(Player player, ClanConfirmMenu menu) {
        // Confere de novo: o clan pode ter mudado enquanto o menu estava aberto.
        Clan clan = clans.getClan(player.getUniqueId());
        if (clan == null || !clan.tag().equals(menu.clanTag()) || !clans.isLeader(player.getUniqueId())) {
            player.sendMessage("§cVocê não é mais o líder deste clan. Nada foi alterado.");
            return;
        }
        UUID target = menu.target();
        String targetName = target == null ? null : ClanManager.nameOf(target);

        switch (menu.action()) {
            case KICK -> {
                if (!clans.removeMember(clan, target)) {
                    player.sendMessage("§c" + targetName + " não é mais membro do clan.");
                    return;
                }
                clans.broadcast(clan, "§e" + targetName + " §7foi expulso do clan.");
                player.sendMessage("§a" + targetName + " foi expulso do clan.");
                Player kicked = Bukkit.getPlayer(target);
                if (kicked != null) {
                    kicked.sendMessage("§cVocê foi expulso do clan [" + clan.tag() + "] " + clan.name() + ".");
                }
            }
            case TRANSFER -> {
                if (!clans.transferLeader(clan, target)) {
                    player.sendMessage("§c" + targetName + " não é mais membro do clan.");
                    return;
                }
                clans.broadcast(clan, "§e" + targetName + " §7é o novo líder do clan.");
            }
            case DISBAND -> {
                // Avisa todos os membros online (inclusive o líder) antes de apagar o clan.
                clans.broadcast(clan, "§cO clan §6[" + clan.tag() + "] " + clan.name() + "§c foi desfeito.");
                clans.disband(clan);
            }
        }
    }

    private void closeLater(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> player.closeInventory());
    }
}
