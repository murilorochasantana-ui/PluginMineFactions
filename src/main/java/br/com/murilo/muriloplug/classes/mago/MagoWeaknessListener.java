package br.com.murilo.muriloplug.classes.mago;

import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Set;

/**
 * Fraqueza do Mago: leva uma porcentagem a mais de dano de golpes e projéteis de outras entidades (jogador, mob,
 * projétil ou magia). Só vale para golpes, nunca para dano por tick (queda, fogo, veneno, afogamento...).
 */
public final class MagoWeaknessListener implements Listener {

    private static final Set<DamageCause> HITS = Set.of(
            DamageCause.ENTITY_ATTACK, DamageCause.ENTITY_SWEEP_ATTACK, DamageCause.PROJECTILE, DamageCause.MAGIC);

    private final ClassManager classes;
    private final MagoSettings settings;

    public MagoWeaknessListener(ClassManager classes, MagoSettings settings) {
        this.classes = classes;
        this.settings = settings;
    }

    // HIGH roda depois do bloqueio de fogo amigo do clan (LOW); ignoreCancelled evita somar em dano já cancelado.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !HITS.contains(event.getCause())
                || !classes.hasClass(victim.getUniqueId(), PlayerClass.MAGO)) {
            return;
        }
        double extra = settings.extraDamage();
        if (extra > 0) {
            event.setDamage(event.getDamage() * (1 + extra));
        }
    }
}
