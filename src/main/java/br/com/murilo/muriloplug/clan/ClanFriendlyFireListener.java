package br.com.murilo.muriloplug.clan;

import br.com.murilo.muriloplug.combat.CombatManager;
import br.com.murilo.muriloplug.teste.TargetRelations;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sem fogo amigo: membros do mesmo clan não causam dano um no outro (golpe direto, projéteis e poções ruins).
 * Roda em prioridade LOW, antes do sistema de combate e do KDA (que olham o dano em MONITOR e ignoram o cancelado).
 */
public final class ClanFriendlyFireListener implements Listener {

    private static final long WARNING_COOLDOWN_MILLIS = 2_000L;

    private final ClanManager clans;
    private final TargetRelations relations;
    private final Map<UUID, Long> lastWarning = new HashMap<>();

    public ClanFriendlyFireListener(ClanManager clans, TargetRelations relations) {
        this.clans = clans;
        this.relations = relations;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = CombatManager.attackerOf(event);
        if (attacker == null) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            // O boneco de teste aliado conta como membro do clan de todos.
            if (event.getEntity() instanceof LivingEntity living && relations.isAlly(attacker, living)) {
                event.setCancelled(true);
                warn(attacker);
            }
            return;
        }
        if (!attacker.equals(victim) && clans.isSameClan(attacker.getUniqueId(), victim.getUniqueId())) {
            event.setCancelled(true);
            warn(attacker);
        }
    }

    /** Poções ruins arremessadas por um membro não afetam os outros membros do clan dele. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        ThrownPotion potion = event.getPotion();
        if (!(potion.getShooter() instanceof Player thrower) || !isHarmful(potion)) {
            return;
        }
        for (LivingEntity affected : new ArrayList<>(event.getAffectedEntities())) {
            if (affected instanceof Player target && !target.equals(thrower)
                    && clans.isSameClan(thrower.getUniqueId(), target.getUniqueId())) {
                event.setIntensity(affected, 0);
                warn(thrower);
            }
        }
    }

    private static boolean isHarmful(ThrownPotion potion) {
        for (PotionEffect effect : potion.getEffects()) {
            if (effect.getType().getEffectCategory() == PotionEffectType.Category.HARMFUL) {
                return true;
            }
        }
        return false;
    }

    /** Avisa o atacante, no máximo uma vez a cada 2 segundos. */
    private void warn(Player attacker) {
        long now = System.currentTimeMillis();
        Long last = lastWarning.get(attacker.getUniqueId());
        if (last == null || now - last >= WARNING_COOLDOWN_MILLIS) {
            lastWarning.put(attacker.getUniqueId(), now);
            attacker.sendMessage("§cVocê não pode atacar um membro do seu clan.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastWarning.remove(event.getPlayer().getUniqueId());
    }
}
