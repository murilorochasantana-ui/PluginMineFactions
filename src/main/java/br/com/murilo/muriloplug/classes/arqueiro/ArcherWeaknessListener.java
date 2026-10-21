package br.com.murilo.muriloplug.classes.arqueiro;

import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

/**
 * Fraqueza do Arqueiro: golpes corpo a corpo com espada ou machado dão menos dano (40% por padrão).
 * Roda em HIGHEST, depois do machado 1.8 (HIGH), então vale sobre o dano final (Afiação, Força e crítico incluídos).
 */
public final class ArcherWeaknessListener implements Listener {

    private final ClassManager classes;
    private final ArqueiroSettings settings;

    public ArcherWeaknessListener(ClassManager classes, ArqueiroSettings settings) {
        this.classes = classes;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if ((event.getCause() != DamageCause.ENTITY_ATTACK && event.getCause() != DamageCause.ENTITY_SWEEP_ATTACK)
                || !(event.getDamager() instanceof Player player)
                || !classes.hasClass(player.getUniqueId(), PlayerClass.ARQUEIRO)) {
            return;
        }
        Material weapon = player.getInventory().getItemInMainHand().getType();
        if (!Tag.ITEMS_SWORDS.isTagged(weapon) && !Tag.ITEMS_AXES.isTagged(weapon)) {
            return;
        }
        double reduction = settings.meleeReduction();
        if (reduction > 0) {
            event.setDamage(event.getDamage() * (1 - reduction));
        }
    }
}
