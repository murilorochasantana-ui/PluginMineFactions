package br.com.murilo.muriloplug.pvp;

import com.google.common.collect.Multimap;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Map;

/**
 * Dano do machado como na 1.8 (madeira 3, ouro 3, pedra 4, ferro 5, diamante 6, netherite 7, já contando o 1 da mão).
 *
 * <p>Troca só a parte da arma: soma ao dano a diferença entre o valor da 1.8 e o dano atual do machado
 * (lido dos atributos do item). Como o crítico multiplica essa parte por 1,5, a diferença também é multiplicada
 * nos golpes críticos. Afiação, força e outros bônus continuam intactos. Espadas não mudam.
 */
public final class AxeDamageListener implements Listener {

    private static final double HAND_DAMAGE = 1.0;
    private static final double CRITICAL_MULTIPLIER = 1.5;

    private static final Map<Material, Double> DAMAGE_1_8 = Map.of(
            Material.WOODEN_AXE, 3.0,
            Material.GOLDEN_AXE, 3.0,
            Material.STONE_AXE, 4.0,
            Material.IRON_AXE, 5.0,
            Material.DIAMOND_AXE, 6.0,
            Material.NETHERITE_AXE, 7.0);

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK || !(event.getDamager() instanceof Player player)) {
            return;
        }
        Material weapon = player.getInventory().getItemInMainHand().getType();
        Double target = DAMAGE_1_8.get(weapon);
        if (target == null) {
            return;
        }

        double current = HAND_DAMAGE + weaponAttackDamage(weapon);
        double difference = target - current;
        double factor = event.isCritical() ? CRITICAL_MULTIPLIER : 1.0;
        event.setDamage(Math.max(0.0, event.getDamage() + difference * factor));
    }

    /** Dano que o item soma ao da mão (atributo ATTACK_DAMAGE de quando está na mão principal). */
    private static double weaponAttackDamage(Material weapon) {
        Multimap<Attribute, AttributeModifier> modifiers = weapon.getDefaultAttributeModifiers(EquipmentSlot.HAND);
        double total = 0.0;
        for (AttributeModifier modifier : modifiers.get(Attribute.ATTACK_DAMAGE)) {
            if (modifier.getOperation() == AttributeModifier.Operation.ADD_NUMBER) {
                total += modifier.getAmount();
            }
        }
        return total;
    }
}
