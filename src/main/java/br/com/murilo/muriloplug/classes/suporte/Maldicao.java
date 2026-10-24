package br.com.murilo.muriloplug.classes.suporte;

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
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Maldição (Shift + clique esquerdo com o Pergaminho): o inimigo na mira causa menos dano em tudo (golpes, flechas,
 * magias: todo dano que tem ele como causador) por alguns segundos. Partículas escuras/roxas no alvo.
 */
public final class Maldicao implements Listener {

    public static final String STATUS = "Amaldiçoado";
    private static final long TICK_PERIOD = 5L;
    private static final Particle.DustOptions DARK = new Particle.DustOptions(Color.fromRGB(60, 0, 80), 1.3f);

    private final JavaPlugin plugin;
    private final SuporteSettings settings;
    private final SuporteTargets targets;
    private final CombatManager combat;
    private final DamageTracker damage;
    private final StatusRegistry statuses;
    /** Fim (ms) da maldição de cada entidade. */
    private final Map<UUID, Long> cursedUntil = new HashMap<>();

    public Maldicao(JavaPlugin plugin, SuporteSettings settings, SuporteTargets targets, CombatManager combat,
                    DamageTracker damage, StatusRegistry statuses) {
        this.plugin = plugin;
        this.settings = settings;
        this.targets = targets;
        this.combat = combat;
        this.damage = damage;
        this.statuses = statuses;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
    }

    public boolean isCursed(UUID id) {
        Long until = cursedUntil.get(id);
        return until != null && until > System.currentTimeMillis();
    }

    /** Tira a maldição (Purificar). */
    public void clear(UUID id) {
        cursedUntil.remove(id);
    }

    /** Amaldiçoa o inimigo na mira. Retorna false (sem gastar nada) se não há inimigo na mira. */
    public boolean cast(Player support) {
        LivingEntity target = targets.aimedTarget(support, settings.curseRange());
        if (target == null || !targets.isEnemy(support, target)) {
            support.sendActionBar(ShopUi.text(target == null ? "§cNenhum inimigo na mira." : "§cSó inimigos podem ser amaldiçoados."));
            return false;
        }
        double seconds = settings.curseSeconds();
        cursedUntil.put(target.getUniqueId(), System.currentTimeMillis() + (long) (seconds * 1000));
        statuses.add(target, STATUS, seconds);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WITCH_CELEBRATE, 1f, 0.7f);
        target.getWorld().spawnParticle(Particle.WITCH, target.getLocation().add(0, 1, 0), 30, 0.4, 0.7, 0.4, 0.05);
        int percent = (int) Math.round(settings.curseReduction() * 100);
        if (target instanceof Player enemy) {
            combat.engage(support, enemy);
            damage.record(enemy.getUniqueId(), support.getUniqueId());
            enemy.sendActionBar(ShopUi.text("§5Você foi amaldiçoado por " + support.getName() + ": -" + percent
                    + "% de dano!"));
            support.sendActionBar(ShopUi.text("§5Você amaldiçoou " + enemy.getName() + "."));
        } else {
            combat.engage(support);
            support.sendActionBar(ShopUi.text("§5Você amaldiçoou o alvo."));
        }
        return true;
    }

    /** O dano causado pelo amaldiçoado sai menor (antes do vínculo e das defesas em HIGHEST). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (cursedUntil.isEmpty() || !(event.getDamageSource().getCausingEntity() instanceof LivingEntity causer)
                || causer.equals(event.getEntity()) || !isCursed(causer.getUniqueId())) {
            return;
        }
        event.setDamage(event.getDamage() * (1 - settings.curseReduction()));
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (UUID id : Set.copyOf(cursedUntil.keySet())) {
            Entity entity = Bukkit.getEntity(id);
            if (entity == null || !entity.isValid() || cursedUntil.get(id) <= now) {
                cursedUntil.remove(id);
            } else if (entity instanceof LivingEntity living) {
                living.getWorld().spawnParticle(Particle.DUST, living.getLocation().add(0, living.getHeight() / 2, 0),
                        5, 0.3, 0.5, 0.3, 0, DARK);
                living.getWorld().spawnParticle(Particle.WITCH, living.getLocation().add(0, living.getHeight(), 0),
                        2, 0.2, 0.1, 0.2, 0);
            }
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        cursedUntil.remove(event.getEntity().getUniqueId());
        statuses.remove(event.getEntity(), STATUS);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cursedUntil.remove(event.getPlayer().getUniqueId());
    }
}
