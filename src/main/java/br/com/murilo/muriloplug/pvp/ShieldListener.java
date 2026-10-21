package br.com.murilo.muriloplug.pvp;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Desativa o escudo: nega o uso do item ao clicar com o botão direito (a interação com blocos não é afetada). */
public final class ShieldListener implements Listener {

    private static final long WARNING_COOLDOWN_MILLIS = 5_000L;

    private final Map<UUID, Long> lastWarning = new HashMap<>();

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (!event.getAction().isRightClick() || item == null || item.getType() != Material.SHIELD) {
            return;
        }
        event.setUseItemInHand(Event.Result.DENY);
        warn(event.getPlayer());
    }

    /** Avisa o jogador, no máximo uma vez a cada 5 segundos. */
    private void warn(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastWarning.get(player.getUniqueId());
        if (last == null || now - last >= WARNING_COOLDOWN_MILLIS) {
            lastWarning.put(player.getUniqueId(), now);
            player.sendMessage("§cEscudos estão desativados neste servidor.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastWarning.remove(event.getPlayer().getUniqueId());
    }
}
