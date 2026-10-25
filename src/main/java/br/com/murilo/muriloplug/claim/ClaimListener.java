package br.com.murilo.muriloplug.claim;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Proteção dos claims: só o dono, os jogadores de confiança e o clan do dono constroem e abrem baús/containers.
 * Exceções contra inimigos: com o clan dono VULNERÁVEL (Núcleo destruído) qualquer inimigo pode quebrar, colocar e
 * abrir; com o clan EM DÍVIDA (claims enfraquecidos) inimigos podem abrir baús e containers.
 */
public final class ClaimListener implements Listener {

    public static final String BYPASS_PERMISSION = "mineplug.claim.bypass";

    private final ClaimManager claims;

    public ClaimListener(ClaimManager claims) {
        this.claims = claims;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        protect(event, event.getPlayer(), event.getBlock().getLocation(), false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        protect(event, event.getPlayer(), event.getBlock().getLocation(), false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        protect(event, event.getPlayer(), event.getBlock().getLocation(), false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        protect(event, event.getPlayer(), event.getBlock().getLocation(), false);
    }

    /** Baús e outros containers (barril, fornalha, funil, dispenser, shulker...) também são protegidos. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onOpen(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null
                || event.useInteractedBlock() == Event.Result.DENY || !isContainer(block)) {
            return;
        }
        Player player = event.getPlayer();
        if (claims.canBuild(player, block.getLocation()) || isOpenToEnemy(block.getLocation(), true)) {
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        player.sendMessage("§cEsta área pertence a outro jogador.");
    }

    // ---- Registro de raids: o que um inimigo conseguiu fazer graças à vulnerabilidade/dívida.

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreakDone(BlockBreakEvent event) {
        reportRaid(event.getPlayer(), event.getBlock(), ClaimRaidAccessEvent.Action.BREAK);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlaceDone(BlockPlaceEvent event) {
        reportRaid(event.getPlayer(), event.getBlock(), ClaimRaidAccessEvent.Action.PLACE);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOpenDone(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && block != null
                && event.useInteractedBlock() != Event.Result.DENY && isContainer(block)) {
            reportRaid(event.getPlayer(), block, ClaimRaidAccessEvent.Action.OPEN);
        }
    }

    private void reportRaid(Player player, Block block, ClaimRaidAccessEvent.Action action) {
        Claim claim = claims.getAt(block.getLocation());
        if (claim == null || claims.canBuild(player, block.getLocation())) {
            return;
        }
        Bukkit.getPluginManager().callEvent(new ClaimRaidAccessEvent(player, claim, block, action, isContainer(block)));
    }

    private static boolean isContainer(Block block) {
        return block.getState(false) instanceof Container;
    }

    private boolean isOpenToEnemy(Location location, boolean container) {
        Claim claim = claims.getAt(location);
        return claim != null && claims.isOpenToEnemy(claim, container);
    }

    private void protect(Cancellable event, Player player, Location location, boolean container) {
        if (claims.canBuild(player, location) || isOpenToEnemy(location, container)) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage("§cEsta área pertence a outro jogador.");
    }
}
