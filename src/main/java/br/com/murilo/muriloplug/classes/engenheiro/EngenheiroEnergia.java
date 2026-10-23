package br.com.murilo.muriloplug.classes.engenheiro;

import br.com.murilo.muriloplug.teste.TestMode;
import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.classes.PlayerClassChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Energia do Engenheiro (barra de XP): de 0 a 100, sobe sempre, como uma bateria. Começa em 0 ao entrar e ao
 * renascer e não é salva. Golems, reparo e as próximas habilidades gastam com {@link #gastarEnergia}.
 */
public final class EngenheiroEnergia implements Listener {

    private static final long PERIOD_TICKS = 20L;

    private final JavaPlugin plugin;
    private final ClassManager classes;
    private final EngenheiroSettings settings;
    private final Map<UUID, Double> energy = new HashMap<>();
    private Consumer<UUID> changeListener = id -> { };
    private TestMode test = TestMode.inactive();

    public EngenheiroEnergia(JavaPlugin plugin, ClassManager classes, EngenheiroSettings settings) {
        this.plugin = plugin;
        this.classes = classes;
        this.settings = settings;
    }

    /** Liga o relógio de 1 em 1 segundo. */
    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, PERIOD_TICKS, PERIOD_TICKS);
    }

    /** Chamado quando a energia muda por gasto (a subida por segundo é lida pelo relógio da barra e do sidebar). */
    public void setChangeListener(Consumer<UUID> changeListener) {
        this.changeListener = changeListener;
    }

    private void tick() {
        double gain = settings.energyPerSecond();
        double max = settings.energyMax();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (classes.hasClass(player.getUniqueId(), PlayerClass.ENGENHEIRO)) {
                energy.merge(player.getUniqueId(), gain, (old, add) -> Math.min(max, old + add));
            }
        }
    }

    /** Modo Teste dos administradores: sem custos nem recargas. */
    public void setTestMode(TestMode test) {
        this.test = test;
    }

    public double getEnergia(UUID id) {
        if (test.isActive(id)) {
            return settings.energyMax(); // Modo Teste: energia sempre máxima
        }
        return Math.min(settings.energyMax(), energy.getOrDefault(id, 0.0));
    }

    public double maxEnergia() {
        return settings.energyMax();
    }

    public boolean temEnergia(UUID id, double amount) {
        return getEnergia(id) >= amount;
    }

    /** Gasta a energia se houver o bastante. Retorna false (sem gastar nada) se não houver. */
    public boolean gastarEnergia(UUID id, double amount) {
        if (test.isActive(id)) {
            return true; // Modo Teste: não gasta
        }
        double current = getEnergia(id);
        if (current < amount) {
            return false;
        }
        energy.put(id, current - amount);
        changeListener.accept(id);
        return true;
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        energy.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        energy.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onClassChange(PlayerClassChangeEvent event) {
        if (event.getOldClass() == PlayerClass.ENGENHEIRO || event.getNewClass() == PlayerClass.ENGENHEIRO) {
            energy.remove(event.getPlayerId());
        }
    }
}
