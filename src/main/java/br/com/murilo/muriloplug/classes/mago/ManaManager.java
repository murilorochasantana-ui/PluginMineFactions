package br.com.murilo.muriloplug.classes.mago;

import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.classes.PlayerClassChangeEvent;
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

/** Mana dos Magos, só em memória: começa cheia ao entrar ou virar Mago e recupera 1 ponto por intervalo. */
public final class ManaManager implements Listener {

    private final JavaPlugin plugin;
    private final ClassManager classes;
    private final MagoSettings settings;
    private final Map<UUID, Integer> mana = new HashMap<>();
    /** Momento (ms) do próximo ponto de mana de cada Mago que não está com a mana cheia. */
    private final Map<UUID, Long> nextRegen = new HashMap<>();
    private Consumer<UUID> changeListener = id -> { };

    public ManaManager(JavaPlugin plugin, ClassManager classes, MagoSettings settings) {
        this.plugin = plugin;
        this.classes = classes;
        this.settings = settings;
    }

    public void setChangeListener(Consumer<UUID> changeListener) {
        this.changeListener = changeListener;
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
            nextRegen.putIfAbsent(player, System.currentTimeMillis() + intervalMillis());
        } else {
            nextRegen.remove(player);
        }
        changeListener.accept(player);
    }

    /** Muda a mana máxima só deste jogador (null = volta ao valor do config) e baixa a atual se passar do novo máximo. */
    public void setMax(UUID player, Integer value) {
        classes.setManaMax(player, value);
        set(player, get(player));
    }

    /** Mana atual (sempre entre 0 e o máximo). Um Mago ainda sem registro conta como cheio. */
    public int get(UUID player) {
        int max = max(player);
        return Math.min(mana.getOrDefault(player, max), max);
    }

    public boolean has(UUID player, int cost) {
        return get(player) >= cost;
    }

    public void spend(UUID player, int cost) {
        int current = get(player);
        if (current >= max(player)) {
            nextRegen.put(player, System.currentTimeMillis() + intervalMillis());
        }
        mana.put(player, Math.max(0, current - cost));
        changeListener.accept(player);
    }

    public void fill(UUID player) {
        mana.put(player, max(player));
        nextRegen.remove(player);
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

    /** Inicia a recuperação de mana (verifica a cada segundo). */
    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::regenerate, 20L, 20L);
    }

    private void regenerate() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (!isMage(id) || get(id) >= max(id)) {
                continue;
            }
            if (now >= nextRegen.getOrDefault(id, 0L)) {
                mana.put(id, get(id) + 1);
                nextRegen.put(id, now + intervalMillis());
                changeListener.accept(id);
            }
        }
    }

    private long intervalMillis() {
        return (long) (Math.max(1, settings.number(MagoSettings.MANA_SECONDS)) * 1000);
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
        nextRegen.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onClassChange(PlayerClassChangeEvent event) {
        UUID id = event.getPlayerId();
        if (event.getNewClass() == PlayerClass.MAGO) {
            fill(id);
        } else {
            mana.remove(id);
            nextRegen.remove(id);
        }
    }
}
