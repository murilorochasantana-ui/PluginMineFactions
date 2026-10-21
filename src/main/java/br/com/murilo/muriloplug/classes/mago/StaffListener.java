package br.com.murilo.muriloplug.classes.mago;

import br.com.murilo.muriloplug.auth.AuthManager;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Clique direito com a staff lança a magia (o comportamento normal do item é sempre cancelado); clique esquerdo
 * dispara o Míssil Arcano.
 */
public final class StaffListener implements Listener {

    private final StaffService service;
    private final AuthManager auth;

    public StaffListener(StaffService service, AuthManager auth) {
        this.service = service;
        this.auth = auth;
    }

    // Não usa ignoreCancelled: um claim alheio cancela o clique em blocos, mas a magia não mexe em blocos.
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            leftClick(event, action);
            return;
        }
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!service.isCaster(event.getItem())) {
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
        StaffType type = service.spellOf(player, event.getItem());
        if (type == null) {
            service.warnNoSpell(player); // Grimório sem magia equipada
        } else if (type == StaffType.TELEPORTE && player.isSneaking()) {
            service.startCharge(player);
        } else {
            service.cast(player, type);
        }
    }

    /**
     * Clique esquerdo do Mago com a staff dispara o Míssil Arcano. Quebrar bloco com a staff é negado, para o
     * clique não minerar e atirar ao mesmo tempo.
     */
    private void leftClick(PlayerInteractEvent event, Action action) {
        if (event.getHand() != EquipmentSlot.HAND || !service.isCaster(event.getItem())
                || !service.isMage(event.getPlayer())) {
            return;
        }
        if (action == Action.LEFT_CLICK_BLOCK) {
            event.setUseInteractedBlock(Event.Result.DENY);
        }
        leftAction(event.getPlayer(), event.getItem());
    }

    /** Shift + esquerdo com o Grimório troca de magia; nos demais casos dispara o Míssil Arcano. */
    private void leftAction(Player player, ItemStack item) {
        if (player.isSneaking() && service.isGrimoire(item)) {
            service.cycleSpell(player);
        } else {
            service.missile(player);
        }
    }

    /** O golpe corpo a corpo do Mago com a staff não causa dano: o clique vira míssil (um só por intervalo). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStaffHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player
                && (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)
                && DamageType.PLAYER_ATTACK.equals(event.getDamageSource().getDamageType())
                && service.isCaster(player.getInventory().getItemInMainHand()) && service.isMage(player)) {
            event.setCancelled(true);
            // Se o clique também gerou LEFT_CLICK_AIR, o intervalo do míssil e da troca evita a ação dupla.
            leftAction(player, player.getInventory().getItemInMainHand());
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
