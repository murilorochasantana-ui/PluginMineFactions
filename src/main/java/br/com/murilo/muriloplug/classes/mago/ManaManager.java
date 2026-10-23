package br.com.murilo.muriloplug.classes.mago;

import br.com.murilo.muriloplug.teste.TestMode;
import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.classes.PlayerClassChangeEvent;
import br.com.murilo.muriloplug.combat.CombatManager;
import br.com.murilo.muriloplug.stats.PlayerKillEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Mana dos Magos, só em memória: começa cheia ao entrar ou virar Mago e recupera 1 ponto por intervalo (mais
 * curto fora de combate). Kill e assistência também dão mana, e o míssil devolve mana por acerto.
 */
public final class ManaManager implements Listener {

    /** A recuperação confere os Magos a cada 10 ticks. */
    private static final long REGEN_CHECK_TICKS = 10L;

    private final JavaPlugin plugin;
    private final ClassManager classes;
    private final MagoSettings settings;
    private final CombatManager combat;
    private final Map<UUID, Integer> mana = new HashMap<>();
    /** Momento (ms) em que começou a contagem do próximo ponto de mana de cada Mago que não está com a mana cheia. */
    private final Map<UUID, Long> regenFrom = new HashMap<>();
    private Consumer<UUID> changeListener = id -> { };
    private TestMode test = TestMode.inactive();

    public ManaManager(JavaPlugin plugin, ClassManager classes, MagoSettings settings, CombatManager combat) {
        this.plugin = plugin;
        this.classes = classes;
        this.settings = settings;
        this.combat = combat;
    }

    public void setChangeListener(Consumer<UUID> changeListener) {
        this.changeListener = changeListener;
    }

    /** Modo Teste dos administradores: sem custos nem recargas. */
    public void setTestMode(TestMode test) {
        this.test = test;
    }

    public boolean isMage(UUID player) {
        return classes.hasClass(player, PlayerClass.MAGO);
    }

    /** Mana máxima do jogador: o valor individual, ou o do config.yml. */
    public int max(UUID player) {
        Integer own = classes.getManaMax(player);
        return own != null ? own : settings.maxMana();
    }

    /** Define a mana atual (limitada a 0..máximo do jogador). */
    public void set(UUID player, int value) {
        int max = max(player);
        int clamped = Math.max(0, Math.min(value, max));
        mana.put(player, clamped);
        if (clamped < max) {
            regenFrom.putIfAbsent(player, System.currentTimeMillis());
        } else {
            regenFrom.remove(player);
        }
        changeListener.accept(player);
    }

    /** Soma mana (sem passar do máximo). */
    public void add(UUID player, int amount) {
        if (amount > 0) {
            set(player, get(player) + amount);
        }
    }

    /** Muda a mana máxima só deste jogador (null = volta ao valor do config) e baixa a atual se passar do novo máximo. */
    public void setMax(UUID player, Integer value) {
        classes.setManaMax(player, value);
        set(player, get(player));
    }

    /** Mana atual (sempre entre 0 e o máximo). Um Mago ainda sem registro conta como cheio. */
    public int get(UUID player) {
        int max = max(player);
        if (test.isActive(player)) {
            return max; // Modo Teste: a mana é sempre máxima
        }
        return Math.min(mana.getOrDefault(player, max), max);
    }

    public boolean has(UUID player, int cost) {
        return get(player) >= cost;
    }

    public void spend(UUID player, int cost) {
        if (test.isActive(player)) {
            return;
        }
        int current = get(player);
        if (current >= max(player)) {
            regenFrom.put(player, System.currentTimeMillis());
        }
        mana.put(player, Math.max(0, current - cost));
        changeListener.accept(player);
    }

    public void fill(UUID player) {
        mana.put(player, max(player));
        regenFrom.remove(player);
        changeListener.accept(player);
    }

    /** Enche a mana dos Magos que já estão online (ao ligar o plugin). */
    public void initOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isMage(player.getUniqueId())) {
                fill(player.getUniqueId());
            }
        }
    }

    /** Inicia a recuperação de mana. */
    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::regenerate, REGEN_CHECK_TICKS, REGEN_CHECK_TICKS);
    }

    private void regenerate() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (!isMage(id) || get(id) >= max(id)) {
                continue;
            }
            long from = regenFrom.computeIfAbsent(id, k -> now);
            // O intervalo vale o do estado atual: entrar em combate no meio da contagem deixa o ponto mais lento.
            long interval = (long) (settings.manaRegenSeconds(combat.isInCombat(player)) * 1000);
            if (now - from >= interval) {
                set(id, get(id) + 1);
                if (get(id) < max(id)) {
                    regenFrom.put(id, now);
                }
            }
        }
    }

    /** Kill dá mana ao killer e, menos, a cada assistente (só para Magos online). */
    @EventHandler
    public void onKill(PlayerKillEvent event) {
        reward(event.getKiller(), settings.manaPerKill());
        for (UUID assistant : event.getAssistants()) {
            reward(assistant, settings.manaPerAssist());
        }
    }

    private void reward(UUID id, int amount) {
        if (amount > 0 && isMage(id) && Bukkit.getPlayer(id) != null) {
            add(id, amount);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (isMage(id)) {
            fill(id);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        mana.remove(event.getPlayer().getUniqueId());
        regenFrom.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onClassChange(PlayerClassChangeEvent event) {
        UUID id = event.getPlayerId();
        if (event.getNewClass() == PlayerClass.MAGO) {
            fill(id);
        } else {
            mana.remove(id);
            regenFrom.remove(id);
        }
    }
}
