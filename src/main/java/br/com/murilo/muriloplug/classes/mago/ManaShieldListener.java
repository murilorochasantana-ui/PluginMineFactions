package br.com.murilo.muriloplug.classes.mago;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import java.util.Set;
import java.util.UUID;

/**
 * Escudo de Mana, passiva do Mago: parte do dano que ele leva (20% por padrão) é absorvida pela mana, 1 ponto de mana
 * para cada 2 de dano absorvido (arredondando para cima). Sem mana suficiente, absorve só o que a mana pagar.
 *
 * <p>Registrar depois dos outros listeners de dano em HIGHEST: ele mede o dano final já ajustado por eles.
 */
public final class ManaShieldListener implements Listener {

    private static final Set<DamageCause> IGNORED = Set.of(
            DamageCause.VOID, DamageCause.KILL, DamageCause.SUICIDE, DamageCause.WORLD_BORDER);

    private final ManaManager mana;
    private final MagoSettings settings;

    public ManaShieldListener(ManaManager mana, MagoSettings settings) {
        this.mana = mana;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || IGNORED.contains(event.getCause())) {
            return;
        }
        UUID id = player.getUniqueId();
        double fraction = settings.shieldFraction();
        double finalDamage = event.getFinalDamage();
        if (!mana.isMage(id) || fraction <= 0 || finalDamage <= 0) {
            return;
        }
        int available = mana.get(id);
        if (available <= 0) {
            return;
        }
        double perMana = settings.shieldDamagePerMana();
        double wanted = finalDamage * fraction;
        int cost = Math.min(available, (int) Math.ceil(wanted / perMana));
        double absorbed = Math.min(wanted, cost * perMana);
        mana.spend(id, cost);
        reduceFinalDamage(event, finalDamage - absorbed);

        player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0, 1, 0), 12, 0.4, 0.6, 0.4, 0.5);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.5f, 1.4f);
    }

    /**
     * Leva o dano final do evento até o alvo. A armadura e a Proteção não são lineares, então ajusta o dano base em
     * poucas rodadas até o final chegar (com folga mínima) no valor pedido.
     */
    private static void reduceFinalDamage(EntityDamageEvent event, double target) {
        for (int i = 0; i < 8; i++) {
            double current = event.getFinalDamage();
            if (current <= 0 || Math.abs(current - target) < 0.01) {
                return;
            }
            event.setDamage(event.getDamage() * (target / current));
        }
    }
}
