package br.com.murilo.muriloplug.classes.mago;

import br.com.murilo.muriloplug.auth.AuthManager;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Clique direito com a staff lança a magia; o comportamento normal do item é sempre cancelado. */
public final class StaffListener implements Listener {

    private final StaffItems items;
    private final StaffService service;
    private final AuthManager auth;

    public StaffListener(StaffItems items, StaffService service, AuthManager auth) {
        this.items = items;
        this.service = service;
        this.auth = auth;
    }

    // Não usa ignoreCancelled: um claim alheio cancela o clique em blocos, mas a magia não mexe em blocos.
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        StaffType type = items.read(event.getItem());
        if (type == null) {
            return;
        }
        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();
        if (clicked != null && clicked.getType().isInteractable() && !player.isSneaking()) {
            return; // deixa abrir baú, porta etc. normalmente
        }
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        if (event.getHand() != EquipmentSlot.HAND || !auth.isLoggedIn(player.getUniqueId())) {
            return;
        }
        if (!service.isMage(player)) {
            service.warnNotMage(player);
            return;
        }
        if (type == StaffType.TELEPORTE && player.isSneaking()) {
            service.startCharge(player);
        } else {
            service.cast(player, type);
        }
    }

    /** Levar dano durante a carga do teleporte cancela, sem gastar nada. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && service.isCharging(player.getUniqueId())
                && event.getFinalDamage() > 0) {
            service.cancelCharge(player, "§cCarga do teleporte cancelada: você levou dano.");
        }
    }

    /** Quem acabou de pousar de um teleporte não leva dano de queda. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && event.getEntity() instanceof Player player
                && service.hasFallProtection(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        service.cancelCharge(event.getEntity(), null);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.forget(event.getPlayer().getUniqueId());
    }
}
