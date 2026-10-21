package br.com.murilo.muriloplug.classes.combatente;

import br.com.murilo.muriloplug.auth.AuthManager;
import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.classes.PlayerClassChangeEvent;
import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Grito de Fúria (Shift + F): Lentidão e golpes mais rápidos por alguns segundos, com recarga contada da ativação.
 * Golpes mais rápidos = a imunidade do alvo depois de cada golpe corpo a corpo do Combatente é encurtada, já que
 * sem recarga de ataque (PvP 1.8) é ela que limita os golpes.
 */
public final class FuryManager implements Listener {

    private static final long TICK_PERIOD = 5L;
    private static final int TICKS_PER_SECOND_UPDATE = 4;

    private final JavaPlugin plugin;
    private final ClassManager classes;
    private final CombatenteSettings settings;
    private final AuthManager auth;
    private final Map<UUID, Long> activeUntil = new HashMap<>();
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();
    private Consumer<UUID> changeListener = id -> { };
    private int ticks;

    public FuryManager(JavaPlugin plugin, ClassManager classes, CombatenteSettings settings, AuthManager auth) {
        this.plugin = plugin;
        this.classes = classes;
        this.settings = settings;
        this.auth = auth;
    }

    public void setChangeListener(Consumer<UUID> changeListener) {
        this.changeListener = changeListener;
    }

    public boolean isActive(UUID id) {
        Long until = activeUntil.get(id);
        return until != null && until > System.currentTimeMillis();
    }

    /** Segundos restantes da fúria (arredondados para cima), ou 0. */
    public int activeSeconds(UUID id) {
        return secondsLeft(activeUntil.get(id));
    }

    /** Segundos restantes da recarga (arredondados para cima), ou 0. */
    public int cooldownSeconds(UUID id) {
        return secondsLeft(cooldownUntil.get(id));
    }

    private static int secondsLeft(Long until) {
        if (until == null) {
            return 0;
        }
        long left = until - System.currentTimeMillis();
        return left <= 0 ? 0 : (int) Math.ceil(left / 1000.0);
    }

    /** Inicia o relógio da fúria (partículas, action bar e sidebar). */
    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
    }

    /** Acaba com todas as fúrias (onDisable). */
    public void endAll() {
        for (UUID id : Set.copyOf(activeUntil.keySet())) {
            end(id);
        }
    }

    private void activate(Player player) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        activeUntil.put(id, now + settings.furyDurationSeconds() * 1000L);
        cooldownUntil.put(id, now + settings.furyCooldownSeconds() * 1000L);
        int level = settings.furySlownessLevel();
        if (level > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                    settings.furyDurationSeconds() * 20, level - 1, false, false, true));
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1f, 1f);
        showActionBar(player);
        changeListener.accept(id);
    }

    /** Acaba com a fúria na hora e tira a lentidão. */
    private void end(UUID id) {
        if (activeUntil.remove(id) == null) {
            return;
        }
        Player player = Bukkit.getPlayer(id);
        if (player != null) {
            player.removePotionEffect(PotionEffectType.SLOWNESS);
        }
        changeListener.accept(id);
    }

    private void showActionBar(Player player) {
        player.sendActionBar(ShopUi.text("§4§lGRITO DE FÚRIA! §c(" + activeSeconds(player.getUniqueId()) + "s)"));
    }

    private void tick() {
        long now = System.currentTimeMillis();
        boolean secondTick = ++ticks % TICKS_PER_SECOND_UPDATE == 0;

        for (UUID id : Set.copyOf(activeUntil.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || player.isDead() || activeUntil.get(id) <= now) {
                end(id);
                continue;
            }
            player.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, player.getLocation().add(0, 1, 0),
                    3, 0.4, 0.6, 0.4, 0);
            if (secondTick) {
                showActionBar(player);
                changeListener.accept(id);
            }
        }

        // A recarga aparece na sidebar: atualiza uma vez por segundo e quando termina.
        Iterator<Map.Entry<UUID, Long>> cooling = cooldownUntil.entrySet().iterator();
        while (cooling.hasNext()) {
            Map.Entry<UUID, Long> entry = cooling.next();
            boolean over = entry.getValue() <= now;
            if (over) {
                cooling.remove();
            }
            if (over || secondTick) {
                changeListener.accept(entry.getKey());
            }
        }
    }

    /** Shift + F: ativa a fúria. Sem estar agachado a tecla F funciona normal. */
    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (!player.isSneaking() || !classes.hasClass(id, PlayerClass.COMBATENTE)) {
            return;
        }
        event.setCancelled(true);
        if (!auth.isLoggedIn(id) || isActive(id)) {
            return;
        }
        int wait = cooldownSeconds(id);
        if (wait > 0) {
            player.sendActionBar(ShopUi.text("§cGrito de Fúria em recarga: " + wait + "s"));
            return;
        }
        activate(player);
    }

    /** Golpe do Combatente em fúria: encurta a imunidade do alvo para o próximo golpe sair ~1.5x mais cedo. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getCause() != DamageCause.ENTITY_ATTACK || !(event.getDamager() instanceof Player attacker)
                || !(event.getEntity() instanceof LivingEntity target) || !isActive(attacker.getUniqueId())
                || event.getFinalDamage() <= 0) {
            return;
        }
        double speed = settings.furyAttackSpeed();
        if (speed <= 1) {
            return;
        }
        // O jogo só aceita novo dano com noDamageTicks <= metade do máximo (10 ticks após o golpe, com máximo 20).
        // Para 1.5x: 10 / 1.5 = 6.67 -> 7 ticks, ou seja, corta 3 ticks. Aplicado no tick seguinte, quando o jogo
        // já definiu a imunidade; é "valor atual - corte", então não acumula entre golpes.
        int half = target.getMaximumNoDamageTicks() / 2;
        int cut = half - (int) Math.round(half / speed);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (target.isValid()) {
                target.setNoDamageTicks(Math.max(0, target.getNoDamageTicks() - cut));
            }
        });
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        end(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        end(id);
        cooldownUntil.remove(id);
    }

    @EventHandler
    public void onClassChange(PlayerClassChangeEvent event) {
        if (event.getOldClass() == PlayerClass.COMBATENTE || event.getNewClass() == PlayerClass.COMBATENTE) {
            end(event.getPlayerId());
            cooldownUntil.remove(event.getPlayerId());
        }
    }
}
