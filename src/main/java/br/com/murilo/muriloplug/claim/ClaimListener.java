package br.com.murilo.muriloplug.claim;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;

public final class ClaimListener implements Listener {

    public static final String BYPASS_PERMISSION = "mineplug.claim.bypass";

    private final ClaimManager claims;

    public ClaimListener(ClaimManager claims) {
        this.claims = claims;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        protect(event, event.getPlayer(), event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        protect(event, event.getPlayer(), event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        protect(event, event.getPlayer(), event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        protect(event, event.getPlayer(), event.getBlock().getLocation());
    }

    private void protect(Cancellable event, Player player, Location location) {
        if (claims.canBuild(player, location)) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage("§cEsta área pertence a outro jogador.");
    }
}
