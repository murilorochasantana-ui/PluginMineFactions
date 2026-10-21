package br.com.murilo.muriloplug.pvp;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Remove o ataque em área da espada: cancela o dano de causa ENTITY_SWEEP_ATTACK. A API do Bukkit não tem como
 * esconder a partícula de sweep, então só o dano é cancelado.
 */
public final class SweepAttackListener implements Listener {

    // LOW: o cancelamento chega antes do sistema de combate e do KDA (que olham o dano em MONITOR).
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            event.setCancelled(true);
        }
    }
}
