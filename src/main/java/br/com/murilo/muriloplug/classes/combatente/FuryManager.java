package br.com.murilo.muriloplug.classes.combatente;

import br.com.murilo.muriloplug.auth.AuthManager;
import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.classes.PlayerClassChangeEvent;
import br.com.murilo.muriloplug.combat.CombatManager;
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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Fúria do Combatente (recurso de 0 a 100, mostrado na barra de XP) e o modo Berserker (Shift + F). A Fúria sobe
 * com golpes e dano levado (ganhos em {@link CombatenteAbilities}) e cai fora de combate. O Berserker, uma vez
 * ativado, não desliga: consome Fúria por segundo, que não sobe enquanto ele dura. Golpes mais rápidos = a imunidade
 * do alvo depois de cada golpe corpo a corpo é encurtada, já que sem recarga de ataque (PvP 1.8) é ela que limita os
 * golpes. Nada é salvo: a Fúria começa em 0 ao entrar e ao renascer.
 */
public final class FuryManager implements Listener {

    private static final long TICK_PERIOD = 5L;
    private static final double TICKS_PER_SECOND = 20.0 / TICK_PERIOD;
    private static final long WARNING_PRIORITY_MILLIS = 3_000L;

    private final JavaPlugin plugin;
    private final ClassManager classes;
    private final CombatenteSettings settings;
    private final AuthManager auth;
    private final CombatManager combat;
    private final Map<UUID, Double> fury = new HashMap<>();
    private final Set<UUID> berserk = new java.util.HashSet<>();
    /** Último momento (ms) de combate ou de ganho de Fúria de cada Combatente. */
    private final Map<UUID, Long> lastActivity = new HashMap<>();
    /** Último momento (ms) de uma mensagem de habilidade, que tem prioridade sobre a dica da action bar. */
    private final Map<UUID, Long> lastWarning = new HashMap<>();
    private Consumer<UUID> changeListener = id -> { };
    private boolean areaDamage;
    private int ticks;

    public FuryManager(JavaPlugin plugin, ClassManager classes, CombatenteSettings settings, AuthManager auth,
                       CombatManager combat) {
        this.plugin = plugin;
        this.classes = classes;
        this.settings = settings;
        this.auth = auth;
        this.combat = combat;
    }

    /** Chamado quando a Fúria ou o Berserker mudam (sidebar e barra de XP). */
    public void setChangeListener(Consumer<UUID> changeListener) {
        this.changeListener = changeListener;
    }

    public CombatenteSettings settings() {
        return settings;
    }

    // ------------------------------------------------------------ consulta

    public double fury(UUID id) {
        return fury.getOrDefault(id, 0.0);
    }

    public boolean isBerserk(UUID id) {
        return berserk.contains(id);
    }

    public boolean isCombatente(UUID id) {
        return classes.hasClass(id, PlayerClass.COMBATENTE);
    }

    // ------------------------------------------------------------ mudança

    /** Soma Fúria (até o máximo). Não sobe durante o Berserker nem para quem não é Combatente. */
    public void add(UUID id, double amount) {
        if (amount <= 0 || berserk.contains(id) || !isCombatente(id)) {
            return;
        }
        set(id, fury(id) + amount);
        lastActivity.put(id, System.currentTimeMillis());
    }

    /** Gasta Fúria. Retorna false (sem gastar) se não houver o bastante. */
    public boolean spend(UUID id, double cost) {
        if (fury(id) < cost) {
            return false;
        }
        set(id, fury(id) - cost);
        return true;
    }

    /** Zera a Fúria e acaba com o Berserker (morte, saída, troca de classe, Último Suspiro). */
    public void reset(UUID id) {
        boolean had = fury.remove(id) != null | berserk.remove(id);
        lastActivity.remove(id);
        if (had) {
            changeListener.accept(id);
        }
    }

    private void set(UUID id, double value) {
        double clamped = Math.max(0, Math.min(settings.furyMax(), value));
        if (clamped == fury(id)) {
            return;
        }
        fury.put(id, clamped);
        changeListener.accept(id);
    }

    /** Mostra uma mensagem de habilidade na action bar (ela tem prioridade sobre a dica). */
    public void warn(Player player, String legacy) {
        lastWarning.put(player.getUniqueId(), System.currentTimeMillis());
        player.sendActionBar(ShopUi.text(legacy));
    }

    public boolean warnedRecently(UUID id) {
        Long last = lastWarning.get(id);
        return last != null && System.currentTimeMillis() - last < WARNING_PRIORITY_MILLIS;
    }

    /** Executa um dano em área do Combatente sem que ele gere Fúria, golpe pulando ou outro bônus de golpe. */
    public void runAreaDamage(Runnable damage) {
        areaDamage = true;
        try {
            damage.run();
        } finally {
            areaDamage = false;
        }
    }

    public boolean isAreaDamage() {
        return areaDamage;
    }

    // ------------------------------------------------------------ relógio

    /** Inicia o relógio da Fúria (consumo, queda, partículas e action bar do Berserker). */
    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
    }

    /** Acaba com todos os Berserkers e zera a Fúria (onDisable). */
    public void endAll() {
        for (UUID id : Set.copyOf(fury.keySet())) {
            reset(id);
        }
        for (UUID id : Set.copyOf(berserk)) {
            reset(id);
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        boolean secondTick = ++ticks % (int) TICKS_PER_SECOND == 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (!isCombatente(id)) {
                continue;
            }
            if (berserk.contains(id)) {
                if (player.isDead()) {
                    reset(id);
                    continue;
                }
                double left = fury(id) - settings.berserkerDrainPerSecond() / TICKS_PER_SECOND;
                if (left <= 0) {
                    reset(id);
                    continue;
                }
                fury.put(id, left);
                changeListener.accept(id);
                player.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, player.getLocation().add(0, 1, 0),
                        3, 0.4, 0.6, 0.4, 0);
                if (secondTick) {
                    showBerserkBar(player);
                }
                continue;
            }
            if (combat.isInCombat(player)) {
                lastActivity.put(id, now);
            }
            Long last = lastActivity.get(id);
            if (fury(id) > 0 && (last == null || now - last > settings.outOfCombatMillis())) {
                set(id, fury(id) - settings.furyDecayPerSecond() / TICKS_PER_SECOND);
            }
        }
    }

    private void showBerserkBar(Player player) {
        player.sendActionBar(ShopUi.text("§4§lBERSERKER! §c(Fúria: " + (int) Math.ceil(fury(player.getUniqueId())) + ")"));
    }

    // ------------------------------------------------------------ Berserker

    /** Shift + F: ativa o Berserker. Sem estar agachado a tecla F fica para a Investida (ou troca de mão normal). */
    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (!player.isSneaking() || !isCombatente(id)) {
            return;
        }
        event.setCancelled(true);
        if (!auth.isLoggedIn(id) || berserk.contains(id)) {
            return;
        }
        double minimum = settings.berserkerMinimum();
        if (fury(id) < minimum) {
            warn(player, "§cFúria insuficiente (" + (int) fury(id) + "/" + (int) minimum + ")");
            return;
        }
        berserk.add(id);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1f, 1f);
        showBerserkBar(player);
        changeListener.accept(id);
    }

    /** Golpe do Combatente em Berserker: encurta a imunidade do alvo para o próximo golpe sair ~1.5x mais cedo. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (areaDamage || event.getCause() != DamageCause.ENTITY_ATTACK || !(event.getDamager() instanceof Player attacker)
                || !(event.getEntity() instanceof LivingEntity target) || !berserk.contains(attacker.getUniqueId())
                || event.getFinalDamage() <= 0) {
            return;
        }
        double speed = settings.berserkerAttackSpeed();
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
        reset(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        reset(id);
        lastWarning.remove(id);
    }

    @EventHandler
    public void onClassChange(PlayerClassChangeEvent event) {
        if (event.getOldClass() == PlayerClass.COMBATENTE || event.getNewClass() == PlayerClass.COMBATENTE) {
            reset(event.getPlayerId());
        }
    }
}
