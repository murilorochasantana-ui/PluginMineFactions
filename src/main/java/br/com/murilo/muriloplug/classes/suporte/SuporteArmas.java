package br.com.murilo.muriloplug.classes.suporte;

import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

/**
 * Fraqueza do Suporte: golpes com espada, machado, maça, tridente ou lança e projéteis de arco, besta e tridente dão
 * menos dano (40% por padrão). Mão vazia e outros itens não têm penalidade. Roda em HIGHEST, depois do machado 1.8
 * (HIGH). O golpe é reconhecido pelo tipo do DamageSource (PLAYER_ATTACK), como na penalidade do Mago.
 */
public final class SuporteArmas implements Listener {

    private final ClassManager classes;
    private final SuporteSettings settings;

    public SuporteArmas(ClassManager classes, SuporteSettings settings) {
        this.classes = classes;
        this.settings = settings;
    }

    private boolean isSupport(Player player) {
        return classes.hasClass(player.getUniqueId(), PlayerClass.SUPORTE);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            if ((event.getCause() == DamageCause.ENTITY_ATTACK || event.getCause() == DamageCause.ENTITY_SWEEP_ATTACK)
                    && DamageType.PLAYER_ATTACK.equals(event.getDamageSource().getDamageType())
                    && isSupport(player) && isWeapon(player.getInventory().getItemInMainHand().getType())) {
                penalize(event);
            }
        } else if (event.getDamager() instanceof AbstractArrow projectile // flechas e tridente arremessado
                && projectile.getShooter() instanceof Player shooter && isSupport(shooter)) {
            penalize(event);
        }
    }

    private void penalize(EntityDamageByEntityEvent event) {
        double penalty = settings.weaponPenalty();
        if (penalty > 0) {
            event.setDamage(event.getDamage() * (1 - penalty));
        }
    }

    /** Espada, machado, maça, tridente ou lança. */
    private static boolean isWeapon(Material material) {
        return Tag.ITEMS_SWORDS.isTagged(material) || Tag.ITEMS_AXES.isTagged(material)
                || material == Material.MACE || material == Material.TRIDENT || material.name().endsWith("_SPEAR");
    }
}
