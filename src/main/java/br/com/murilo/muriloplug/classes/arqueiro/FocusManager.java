package br.com.murilo.muriloplug.classes.arqueiro;

import br.com.murilo.muriloplug.teste.TestMode;
import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.classes.PlayerClassChangeEvent;
import br.com.murilo.muriloplug.combat.CombatManager;
import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Foco do Arqueiro (recurso de 0 a 100, mostrado na barra de XP). Fora de combate sobe com o tempo; em combate só
 * sobe com flechas que acertam inimigos (ganho feito em {@link ArcherDamageListener}). Nada é salvo: o Foco começa
 * em 0 ao entrar e ao renascer.
 */
public final class FocusManager implements Listener {

    private static final long WARNING_PRIORITY_MILLIS = 3_000L;

    private final JavaPlugin plugin;
    private final ClassManager classes;
    private final ArqueiroSettings settings;
    private final CombatManager combat;
    private final Map<UUID, Integer> focus = new HashMap<>();
    /** Último momento (ms) de uma mensagem de habilidade, que tem prioridade sobre a dica da action bar. */
    private final Map<UUID, Long> lastWarning = new HashMap<>();
    private Consumer<UUID> changeListener = id -> { };
    private TestMode test = TestMode.inactive();

    public FocusManager(JavaPlugin plugin, ClassManager classes, ArqueiroSettings settings, CombatManager combat) {
        this.plugin = plugin;
        this.classes = classes;
        this.settings = settings;
        this.combat = combat;
    }

    /** Chamado quando o Foco muda (sidebar e barra de XP). */
    public void setChangeListener(Consumer<UUID> changeListener) {
        this.changeListener = changeListener;
    }

    public int get(UUID id) {
        return test.isActive(id) ? settings.focusMax() : focus.getOrDefault(id, 0);
    }

    /** Modo Teste dos administradores: sem custos nem recargas. */
    public void setTestMode(TestMode test) {
        this.test = test;
    }

    public int max() {
        return settings.focusMax();
    }

    public boolean has(UUID id, int cost) {
        return get(id) >= cost;
    }

    /** Soma Foco (até o máximo) para quem é Arqueiro. */
    public void add(UUID id, int amount) {
        if (amount > 0 && classes.hasClass(id, PlayerClass.ARQUEIRO)) {
            set(id, get(id) + amount);
        }
    }

    /** Gasta Foco. Retorna false (sem gastar) se não houver o bastante. */
    public boolean spend(UUID id, int cost) {
        if (test.isActive(id)) {
            return true; // Modo Teste: não gasta
        }
        if (get(id) < cost) {
            return false;
        }
        set(id, get(id) - cost);
        return true;
    }

    public void reset(UUID id) {
        if (focus.remove(id) != null) {
            changeListener.accept(id);
        }
    }

    private void set(UUID id, int value) {
        int clamped = Math.max(0, Math.min(settings.focusMax(), value));
        if (clamped == get(id)) {
            return;
        }
        focus.put(id, clamped);
        changeListener.accept(id);
    }

    /** Mostra uma mensagem de habilidade na action bar (ela tem prioridade sobre a dica). */
    public void warn(Player player, String legacy) {
        lastWarning.put(player.getUniqueId(), System.currentTimeMillis());
        player.sendActionBar(ShopUi.text(legacy));
    }

    public void warnInsufficient(Player player, int cost) {
        warn(player, "§cFoco insuficiente (" + get(player.getUniqueId()) + "/" + cost + ")");
    }

    public boolean warnedRecently(UUID id) {
        Long last = lastWarning.get(id);
        return last != null && System.currentTimeMillis() - last < WARNING_PRIORITY_MILLIS;
    }

    /** Liga a regeneração fora de combate. */
    public void start() {
        long period = settings.focusRegenIntervalTicks();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::regen, period, period);
    }

    private void regen() {
        int amount = settings.focusRegen();
        if (amount <= 0) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isDead() && !combat.isInCombat(player)) {
                add(player.getUniqueId(), amount);
            }
        }
    }

    public void endAll() {
        for (UUID id : Set.copyOf(focus.keySet())) {
            reset(id);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        reset(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        reset(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        focus.remove(id);
        lastWarning.remove(id);
    }

    @EventHandler
    public void onClassChange(PlayerClassChangeEvent event) {
        if (event.getOldClass() == PlayerClass.ARQUEIRO || event.getNewClass() == PlayerClass.ARQUEIRO) {
            reset(event.getPlayerId());
        }
    }
}
