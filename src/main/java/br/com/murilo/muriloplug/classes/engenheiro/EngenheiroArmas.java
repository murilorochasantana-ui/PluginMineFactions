package br.com.murilo.muriloplug.classes.engenheiro;

import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import org.bukkit.Tag;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

/**
 * Armas do Engenheiro: espada com menos dano, flechas do arco com menos dano e da besta com mais. Roda em HIGHEST,
 * depois do PvP 1.8 (HIGH), então vale sobre o dano final.
 */
public final class EngenheiroArmas implements Listener {

    private final ClassManager classes;
    private final EngenheiroSettings settings;

    public EngenheiroArmas(ClassManager classes, EngenheiroSettings settings) {
        this.classes = classes;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        double adjust = 0;
        if (event.getCause() == DamageCause.ENTITY_ATTACK || event.getCause() == DamageCause.ENTITY_SWEEP_ATTACK) {
            if (event.getDamager() instanceof Player player && isEngineer(player)
                    && Tag.ITEMS_SWORDS.isTagged(player.getInventory().getItemInMainHand().getType())) {
                adjust = settings.swordDamage();
            }
        } else if (event.getCause() == DamageCause.PROJECTILE && event.getDamager() instanceof AbstractArrow arrow
                && !(arrow instanceof Trident) && arrow.getShooter() instanceof Player shooter && isEngineer(shooter)) {
            adjust = arrow.isShotFromCrossbow() ? settings.crossbowDamage() : settings.bowDamage();
        }
        if (adjust != 0) {
            event.setDamage(Math.max(0, event.getDamage() * (1 + adjust)));
        }
    }

    private boolean isEngineer(Player player) {
        return classes.hasClass(player.getUniqueId(), PlayerClass.ENGENHEIRO);
    }
}
