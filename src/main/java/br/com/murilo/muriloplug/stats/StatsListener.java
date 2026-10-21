package br.com.murilo.muriloplug.stats;

import br.com.murilo.muriloplug.combat.CombatManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class StatsListener implements Listener {

    private final StatsManager stats;
    private final DamageTracker damage;

    public StatsListener(StatsManager stats, DamageTracker damage) {
        this.stats = stats;
        this.damage = damage;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = CombatManager.attackerOf(event);
        if (attacker != null && !attacker.equals(victim)) {
            damage.record(victim.getUniqueId(), attacker.getUniqueId());
        }
    }

    // HIGHEST para poder trocar a mensagem de morte do jogo pela nossa.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        UUID victimId = victim.getUniqueId();
        List<UUID> attackers = damage.recentAttackers(victimId);
        damage.clear(victimId);

        // Kill: quem matou; sem killer (queda, lava, void, fogo...), o último jogador que bateu nos últimos 30s.
        Player directKiller = victim.getKiller();
        UUID killer;
        if (directKiller != null && !directKiller.equals(victim)) {
            killer = directKiller.getUniqueId();
        } else if (!attackers.isEmpty()) {
            killer = attackers.get(attackers.size() - 1);
        } else {
            return; // ninguém ganhou a kill: a morte não conta
        }

        List<UUID> assistants = new ArrayList<>(attackers);
        assistants.remove(killer);
        assistants.remove(victimId);

        stats.recordKill(killer, victimId, assistants);

        Component message = Component.text(victim.getName(), NamedTextColor.RED)
                .append(Component.text(" foi morto por ", NamedTextColor.GRAY))
                .append(Component.text(nameOf(killer), NamedTextColor.GREEN));
        if (!assistants.isEmpty()) {
            List<String> names = assistants.stream().map(StatsListener::nameOf).toList();
            message = message.append(Component.text(" (assistência: " + String.join(", ", names) + ")",
                    NamedTextColor.GRAY));
        }
        event.deathMessage(message);
    }

    private static String nameOf(UUID id) {
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            return online.getName();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(id);
        return offline.getName() != null ? offline.getName() : id.toString();
    }
}
