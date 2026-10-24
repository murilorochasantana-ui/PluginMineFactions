package br.com.murilo.muriloplug.classes.suporte;

import br.com.murilo.muriloplug.classes.SilenceManager;
import br.com.murilo.muriloplug.combat.CombatManager;
import br.com.murilo.muriloplug.shop.ShopUi;
import br.com.murilo.muriloplug.stats.DamageTracker;
import br.com.murilo.muriloplug.teste.StatusRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Silêncio (Shift + clique esquerdo com a Estrela do Nether): o inimigo na mira não usa nenhuma habilidade de classe
 * por alguns segundos. O bloqueio em si fica no {@link SilenceManager}, que todas as classes consultam; aqui ficam a
 * mira, o status e as partículas cinzas em cima do alvo.
 */
public final class Silencio implements Listener {

    public static final String STATUS = "Silenciado";
    private static final long TICK_PERIOD = 5L;
    private static final Particle.DustOptions GRAY = new Particle.DustOptions(Color.fromRGB(120, 120, 120), 1.2f);

    private final JavaPlugin plugin;
    private final SuporteSettings settings;
    private final SuporteTargets targets;
    private final CombatManager combat;
    private final DamageTracker damage;
    private final StatusRegistry statuses;

    public Silencio(JavaPlugin plugin, SuporteSettings settings, SuporteTargets targets, CombatManager combat,
                    DamageTracker damage, StatusRegistry statuses) {
        this.plugin = plugin;
        this.settings = settings;
        this.targets = targets;
        this.combat = combat;
        this.damage = damage;
        this.statuses = statuses;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::particles, TICK_PERIOD, TICK_PERIOD);
    }

    /** Silencia o inimigo na mira. Retorna false (sem gastar nada) se não há inimigo na mira. */
    public boolean cast(Player support) {
        LivingEntity target = targets.aimedTarget(support, settings.silenceRange());
        if (target == null || !targets.isEnemy(support, target)) {
            support.sendActionBar(ShopUi.text(target == null ? "§cNenhum inimigo na mira." : "§cSó inimigos podem ser silenciados."));
            return false;
        }
        double seconds = settings.silenceSeconds();
        SilenceManager.silence(target, seconds);
        statuses.add(target, STATUS, seconds);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 0.6f);
        target.getWorld().spawnParticle(Particle.SMOKE, target.getEyeLocation(), 20, 0.3, 0.3, 0.3, 0.02);
        if (target instanceof Player enemy) {
            combat.engage(support, enemy);
            damage.record(enemy.getUniqueId(), support.getUniqueId());
            enemy.sendActionBar(ShopUi.text("§8Você foi silenciado por " + support.getName() + "! ("
                    + SuporteItems.format(seconds) + "s)"));
            support.sendActionBar(ShopUi.text("§8Você silenciou " + enemy.getName() + "."));
        } else {
            combat.engage(support);
            support.sendActionBar(ShopUi.text("§8Você silenciou o alvo."));
        }
        return true;
    }

    private void particles() {
        for (UUID id : SilenceManager.silenced()) {
            Entity entity = Bukkit.getEntity(id);
            if (entity instanceof LivingEntity living && living.isValid()) {
                living.getWorld().spawnParticle(Particle.DUST, living.getLocation().add(0, living.getHeight() + 0.4, 0),
                        4, 0.25, 0.08, 0.25, 0, GRAY);
            }
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        SilenceManager.clear(event.getEntity().getUniqueId());
        statuses.remove(event.getEntity(), STATUS);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        SilenceManager.clear(event.getPlayer().getUniqueId());
    }
}
