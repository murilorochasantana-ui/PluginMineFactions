package br.com.murilo.muriloplug.classes.suporte;

import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.classes.PlayerClassChangeEvent;
import br.com.murilo.muriloplug.shop.ShopUi;
import br.com.murilo.muriloplug.stats.PlayerKillEvent;
import br.com.murilo.muriloplug.teste.TestMode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Harmonia do Suporte (barra de XP), de 0 a 100. Sobe curando aliados ou a si mesmo, com o dano que o vínculo tira
 * do aliado, por assistência (passiva Recompensa) e com o tempo. As habilidades gastam com {@link #gastarHarmonia}.
 * Começa em 0 ao entrar e ao renascer e não é salva.
 */
public final class HarmoniaManager implements Listener {

    private static final long PERIOD_TICKS = 20L;

    private final JavaPlugin plugin;
    private final ClassManager classes;
    private final SuporteSettings settings;
    private final Map<UUID, Double> harmony = new HashMap<>();
    private Consumer<UUID> changeListener = id -> { };
    private TestMode test = TestMode.inactive();
    private int seconds;

    public HarmoniaManager(JavaPlugin plugin, ClassManager classes, SuporteSettings settings) {
        this.plugin = plugin;
        this.classes = classes;
        this.settings = settings;
    }

    /** Liga o relógio de 1 em 1 segundo (Harmonia com o tempo). */
    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, PERIOD_TICKS, PERIOD_TICKS);
    }

    /** Chamado quando a Harmonia muda (sidebar e barra de XP). */
    public void setChangeListener(Consumer<UUID> changeListener) {
        this.changeListener = changeListener;
    }

    /** Modo Teste dos administradores: Harmonia sempre máxima, sem gasto. */
    public void setTestMode(TestMode test) {
        this.test = test;
    }

    private void tick() {
        if (++seconds % settings.harmonyRegenSeconds() != 0) {
            return;
        }
        double gain = settings.harmonyRegen();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isDead()) {
                add(player.getUniqueId(), gain);
            }
        }
    }

    // ------------------------------------------------------------ consulta

    public double getHarmonia(UUID id) {
        if (test.isActive(id)) {
            return settings.harmonyMax(); // Modo Teste: Harmonia sempre máxima
        }
        return Math.min(settings.harmonyMax(), harmony.getOrDefault(id, 0.0));
    }

    public double maxHarmonia() {
        return settings.harmonyMax();
    }

    public boolean temHarmonia(UUID id, double amount) {
        return getHarmonia(id) >= amount;
    }

    /**
     * Confere se há Harmonia para a habilidade; se não houver, avisa "Harmonia insuficiente (X/Y)" na action bar.
     * Não gasta nada.
     */
    public boolean check(Player player, double cost) {
        if (temHarmonia(player.getUniqueId(), cost)) {
            return true;
        }
        player.sendActionBar(ShopUi.text("§cHarmonia insuficiente (" + (int) Math.floor(getHarmonia(player.getUniqueId()))
                + "/" + (int) Math.ceil(cost) + ")"));
        return false;
    }

    // ------------------------------------------------------------ mudança

    /** Gasta a Harmonia se houver o bastante. Retorna false (sem gastar nada) se não houver. */
    public boolean gastarHarmonia(UUID id, double amount) {
        if (test.isActive(id) || amount <= 0) {
            return true; // Modo Teste: não gasta
        }
        double current = getHarmonia(id);
        if (current < amount) {
            return false;
        }
        harmony.put(id, current - amount);
        changeListener.accept(id);
        return true;
    }

    /** Soma Harmonia (até o máximo). Só para Suportes. */
    public void add(UUID id, double amount) {
        if (amount <= 0 || !classes.hasClass(id, PlayerClass.SUPORTE) || test.isActive(id)) {
            return;
        }
        double current = harmony.getOrDefault(id, 0.0);
        double updated = Math.min(settings.harmonyMax(), current + amount);
        if (updated == current) {
            return;
        }
        harmony.put(id, updated);
        // A barra e a sidebar mostram números inteiros: só avisa quando o inteiro muda.
        if ((int) Math.floor(updated) != (int) Math.floor(current)) {
            changeListener.accept(id);
        }
    }

    /** Ganho por cura feita pelo Suporte (em aliados ou em si mesmo). */
    public void addForHeal(UUID id, double healed) {
        add(id, healed * settings.harmonyPerHeal());
    }

    /** Ganho pelo dano que o vínculo tirou do aliado. */
    public void addForLink(UUID id, double shared) {
        add(id, shared * settings.harmonyPerLink());
    }

    private void reset(UUID id) {
        if (harmony.remove(id) != null) {
            changeListener.accept(id);
        }
    }

    /** Passiva Recompensa: cada assistência do Suporte dá Harmonia (além do dinheiro). */
    @EventHandler
    public void onKill(PlayerKillEvent event) {
        for (UUID assistant : event.getAssistants()) {
            add(assistant, settings.harmonyPerAssist());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        reset(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        reset(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        harmony.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onClassChange(PlayerClassChangeEvent event) {
        if (event.getOldClass() == PlayerClass.SUPORTE || event.getNewClass() == PlayerClass.SUPORTE) {
            reset(event.getPlayerId());
        }
    }
}
