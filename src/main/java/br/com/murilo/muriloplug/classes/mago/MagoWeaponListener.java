package br.com.murilo.muriloplug.classes.mago;

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
 * Penalidade de arma do Mago (os 10% de combate): golpes com espada, machado, maça, tridente ou lança e flechas de
 * arco ou besta dão menos dano (60% por padrão). Mão vazia e outros itens não têm penalidade. Golpear com arma
 * também zera o Poder Arcano. Roda em HIGHEST, depois do machado 1.8 (HIGH).
 *
 * <p>O golpe é reconhecido pelo tipo do DamageSource (PLAYER_ATTACK), não só pela causa: o dano das magias também
 * tem o Mago como causador direto e não pode ser tratado como golpe.
 */
public final class MagoWeaponListener implements Listener {

    private final ClassManager classes;
    private final MagoSettings settings;
    private final ArcanePower arcane;

    public MagoWeaponListener(ClassManager classes, MagoSettings settings, ArcanePower arcane) {
        this.classes = classes;
        this.settings = settings;
        this.arcane = arcane;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            if ((event.getCause() != DamageCause.ENTITY_ATTACK && event.getCause() != DamageCause.ENTITY_SWEEP_ATTACK)
                    || !DamageType.PLAYER_ATTACK.equals(event.getDamageSource().getDamageType())
                    || !classes.hasClass(player.getUniqueId(), PlayerClass.MAGO)
                    || !isWeapon(player.getInventory().getItemInMainHand().getType())) {
                return;
            }
            penalize(event);
            arcane.reset(player.getUniqueId());
        } else if (event.getDamager() instanceof AbstractArrow arrow && arrow.getShooter() instanceof Player shooter
                && classes.hasClass(shooter.getUniqueId(), PlayerClass.MAGO)) {
            penalize(event);
        }
    }

    private void penalize(EntityDamageByEntityEvent event) {
        double penalty = settings.weaponPenalty();
        if (penalty > 0) {
            event.setDamage(event.getDamage() * (1 - penalty));
        }
    }

    /** Espada, machado, maça, tridente ou lança (as lanças só existem em versões mais novas que a 1.21.4). */
    private static boolean isWeapon(Material material) {
        return Tag.ITEMS_SWORDS.isTagged(material) || Tag.ITEMS_AXES.isTagged(material)
                || material == Material.MACE || material == Material.TRIDENT || material.name().endsWith("_SPEAR");
    }
}
