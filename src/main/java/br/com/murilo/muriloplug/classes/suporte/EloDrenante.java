package br.com.murilo.muriloplug.classes.suporte;

import br.com.murilo.muriloplug.shop.ShopUi;
import br.com.murilo.muriloplug.teste.StatusRegistry;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Elo Drenante (Shift + clique esquerdo com o Núcleo): por alguns segundos, todo dano que o Suporte causa (golpes,
 * flechas, habilidades: tudo que tem o Suporte como causador) vira cura no aliado vinculado. Mede o dano final em
 * MONITOR, depois de todas as reduções.
 */
public final class EloDrenante implements Listener {

    public static final String STATUS = "Elo Drenante";
    private static final Particle.DustOptions PURPLE = new Particle.DustOptions(Color.fromRGB(170, 40, 230), 1.3f);

    private final SuporteSettings settings;
    private final LinkManager links;
    private final HarmoniaManager harmony;
    private final StatusRegistry statuses;
    /** Fim (ms) do Elo de cada Suporte. */
    private final Map<UUID, Long> activeUntil = new HashMap<>();

    public EloDrenante(SuporteSettings settings, LinkManager links, HarmoniaManager harmony, StatusRegistry statuses) {
        this.settings = settings;
        this.links = links;
        this.harmony = harmony;
        this.statuses = statuses;
    }

    public boolean isActive(UUID support) {
        Long until = activeUntil.get(support);
        return until != null && until > System.currentTimeMillis();
    }

    /** Liga o Elo. Retorna false (sem gastar nada) se não há aliado vinculado. */
    public boolean activate(Player support) {
        if (links.linkedAlly(support.getUniqueId()) == null) {
            support.sendActionBar(ShopUi.text("§cVocê precisa estar vinculado a um aliado."));
            return false;
        }
        double seconds = settings.drainSeconds();
        activeUntil.put(support.getUniqueId(), System.currentTimeMillis() + (long) (seconds * 1000));
        statuses.add(support, STATUS, seconds);
        support.getWorld().playSound(support.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1f, 1.2f);
        support.getWorld().spawnParticle(Particle.DUST, support.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0, PURPLE);
        support.sendActionBar(ShopUi.text("§5Elo Drenante! §7(" + SuporteItems.format(seconds) + "s)"));
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (activeUntil.isEmpty() || !(event.getDamageSource().getCausingEntity() instanceof Player support)
                || !isActive(support.getUniqueId()) || event.getEntity().equals(support)) {
            return;
        }
        LivingEntity ally = links.linkedAlly(support.getUniqueId());
        double amount = event.getFinalDamage() * settings.drainPercent();
        if (ally == null || ally.equals(event.getEntity()) || amount <= 0) {
            return;
        }
        AttributeInstance max = ally.getAttribute(Attribute.MAX_HEALTH);
        double limit = max == null ? 20 : max.getValue();
        double before = ally.getHealth();
        ally.setHealth(Math.min(limit, before + amount));
        double gained = ally.getHealth() - before;
        ally.getWorld().spawnParticle(Particle.DUST, ally.getLocation().add(0, 1, 0), 12, 0.35, 0.6, 0.35, 0, PURPLE);
        if (gained > 0) {
            harmony.addForHeal(support.getUniqueId(), gained);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        end(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        end(event.getPlayer().getUniqueId());
    }

    public void end(UUID support) {
        if (activeUntil.remove(support) != null) {
            statuses.remove(support, STATUS);
        }
    }
}
