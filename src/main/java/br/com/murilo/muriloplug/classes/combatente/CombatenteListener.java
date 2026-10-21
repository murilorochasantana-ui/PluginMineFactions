package br.com.murilo.muriloplug.classes.combatente;

import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Passiva do Combatente (30% menos dano de qualquer origem) e a fraqueza (não pode usar arco). */
public final class CombatenteListener implements Listener {

    private static final Set<DamageCause> EXEMPT = Set.of(
            DamageCause.VOID, DamageCause.KILL, DamageCause.SUICIDE, DamageCause.WORLD_BORDER);
    private static final long WARNING_COOLDOWN_MILLIS = 2_000L;

    private final ClassManager classes;
    private final CombatenteSettings settings;
    private final Map<UUID, Long> lastWarning = new HashMap<>();

    public CombatenteListener(ClassManager classes, CombatenteSettings settings) {
        this.classes = classes;
        this.settings = settings;
    }

    private boolean isCombatente(Player player) {
        return classes.hasClass(player.getUniqueId(), PlayerClass.COMBATENTE);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || EXEMPT.contains(event.getCause())
                || !isCombatente(player)) {
            return;
        }
        double reduction = settings.damageReduction();
        if (reduction > 0) {
            event.setDamage(event.getDamage() * (1 - reduction));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || !isCombatente(player) || event.getBow() == null) {
            return;
        }
        Material bow = event.getBow().getType();
        if (bow != Material.BOW && !(bow == Material.CROSSBOW && settings.blockCrossbow())) {
            return;
        }
        event.setCancelled(true);
        player.updateInventory();
        long now = System.currentTimeMillis();
        Long last = lastWarning.get(player.getUniqueId());
        if (last == null || now - last >= WARNING_COOLDOWN_MILLIS) {
            lastWarning.put(player.getUniqueId(), now);
            player.sendMessage("§cCombatentes não podem usar arco.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastWarning.remove(event.getPlayer().getUniqueId());
    }
}
