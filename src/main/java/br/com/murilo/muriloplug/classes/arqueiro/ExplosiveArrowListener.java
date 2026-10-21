package br.com.murilo.muriloplug.classes.arqueiro;

import org.bukkit.Location;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Efeitos da Flecha Explosiva: dano 3x ao acertar um jogador e uma explosão no impacto (sem quebrar blocos nem
 * colocar fogo, com o Arqueiro como causador). A explosão sai 1 tick depois do impacto, para o dano da flecha
 * ser aplicado antes dela. O fogo amigo do clan cancela o dano da explosão, pois o causador é o próprio Arqueiro.
 */
public final class ExplosiveArrowListener implements Listener {

    private final JavaPlugin plugin;
    private final ArqueiroSettings settings;
    private final ArrowItems arrows;
    /** Flechas que já têm a explosão agendada. */
    private final Set<UUID> pending = new HashSet<>();
    /** Arqueiro cuja explosão está sendo criada agora (os eventos de dano dela saem de dentro da criação). */
    private UUID exploding;

    public ExplosiveArrowListener(JavaPlugin plugin, ArqueiroSettings settings, ArrowItems arrows) {
        this.plugin = plugin;
        this.settings = settings;
        this.arrows = arrows;
    }

    /** Dano da flecha x3 só no acerto direto em jogador. Roda depois do bloqueio de fogo amigo (LOW). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArrowDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof AbstractArrow arrow && event.getEntity() instanceof Player
                && arrows.isExplosiveProjectile(arrow.getPersistentDataContainer())) {
            event.setDamage(event.getDamage() * settings.explosiveDamageMultiplier());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow)
                || !arrows.isExplosiveProjectile(arrow.getPersistentDataContainer())
                || !pending.add(arrow.getUniqueId())) {
            return;
        }
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        Location impact = arrow.getLocation().clone();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            pending.remove(arrow.getUniqueId());
            arrow.remove();
            if (arrow.getShooter() instanceof Player shooter && shooter.isOnline() && impact.getWorld() != null) {
                explode(shooter, impact);
            }
        });
    }

    private void explode(Player shooter, Location impact) {
        exploding = shooter.getUniqueId();
        try {
            impact.getWorld().createExplosion(impact, settings.explosionPower(), false, false, shooter);
        } finally {
            exploding = null;
        }
    }

    /** Durante a explosão: poupa itens no chão, molduras, quadros, suportes de armadura e (se configurado) o Arqueiro. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onExplosionDamage(EntityDamageByEntityEvent event) {
        if (exploding == null || event.getCause() != DamageCause.ENTITY_EXPLOSION) {
            return;
        }
        Entity victim = event.getEntity();
        if (victim instanceof Item || victim instanceof Hanging || victim instanceof ArmorStand
                || (!settings.selfDamage() && victim.getUniqueId().equals(exploding))) {
            event.setCancelled(true);
        }
    }

    /** Molduras e quadros também podem quebrar por um evento próprio. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (exploding != null && event.getCause() == HangingBreakEvent.RemoveCause.EXPLOSION) {
            event.setCancelled(true);
        }
    }
}
