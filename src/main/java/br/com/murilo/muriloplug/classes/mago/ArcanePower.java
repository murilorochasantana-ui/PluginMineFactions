package br.com.murilo.muriloplug.classes.mago;

import br.com.murilo.muriloplug.classes.PlayerClassChangeEvent;
import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Poder Arcano, passiva do Mago: cada magia lançada dá 1 acúmulo (até o máximo do config) e reinicia o tempo; todos
 * os acúmulos somem juntos quando o tempo acaba. Cada acúmulo aumenta o dano das magias e do míssil.
 */
public final class ArcanePower implements Listener {

    /** A action bar com os acúmulos é refeita a cada 2s (ela some depois de ~3s). */
    private static final long BAR_TICKS = 40L;

    private final JavaPlugin plugin;
    private final ManaManager mana;
    private final MagoSettings settings;
    private final Map<UUID, Integer> stacks = new HashMap<>();
    private final Map<UUID, Long> expiresAt = new HashMap<>();

    public ArcanePower(JavaPlugin plugin, ManaManager mana, MagoSettings settings) {
        this.plugin = plugin;
        this.mana = mana;
        this.settings = settings;
    }

    /** Acúmulos atuais (0 se acabou o tempo). */
    public int stacks(UUID player) {
        Long end = expiresAt.get(player);
        if (end == null) {
            return 0;
        }
        if (end <= System.currentTimeMillis()) {
            reset(player);
            return 0;
        }
        return stacks.getOrDefault(player, 0);
    }

    /** Soma 1 acúmulo (até o máximo) e reinicia o tempo. */
    public void gain(UUID player) {
        int max = settings.arcaneMax();
        if (max <= 0) {
            return;
        }
        stacks.put(player, Math.min(max, stacks(player) + 1));
        expiresAt.put(player, System.currentTimeMillis() + (long) (settings.arcaneSeconds() * 1000));
    }

    public void reset(UUID player) {
        stacks.remove(player);
        expiresAt.remove(player);
    }

    /** Multiplicador de dano das magias e do míssil (1.0 sem acúmulos). */
    public double multiplier(UUID player) {
        return 1 + settings.arcaneBonus() * stacks(player);
    }

    /** Trecho para a action bar (" §8| §5Poder Arcano x2"), ou vazio sem acúmulos. */
    public String label(UUID player) {
        int count = stacks(player);
        return count > 0 ? " §8| §5Poder Arcano x" + count : "";
    }

    /** Mantém na action bar a mana e os acúmulos de quem tem Poder Arcano ativo. */
    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (UUID id : new ArrayList<>(stacks.keySet())) {
                Player player = Bukkit.getPlayer(id);
                if (player == null || stacks(id) <= 0) {
                    continue;
                }
                player.sendActionBar(ShopUi.text("§b" + mana.get(id) + "/" + mana.max(id) + " mana" + label(id)));
            }
        }, BAR_TICKS, BAR_TICKS);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        reset(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        reset(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onClassChange(PlayerClassChangeEvent event) {
        reset(event.getPlayerId());
    }
}
