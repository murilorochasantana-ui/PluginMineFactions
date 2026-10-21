package br.com.murilo.muriloplug.economy;

import br.com.murilo.muriloplug.stats.PlayerKillEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/** Paga recompensa em dinheiro por kills e assistências (valores no config.yml). */
public final class RewardListener implements Listener {

    public static final String KILL_PATH = "economia.recompensa-kill";
    public static final String ASSIST_PATH = "economia.recompensa-assistencia";
    public static final long DEFAULT_KILL_REWARD = 100;
    public static final long DEFAULT_ASSIST_REWARD = 30;

    private final JavaPlugin plugin;
    private final EconomyManager economy;

    public RewardListener(JavaPlugin plugin, EconomyManager economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    @EventHandler
    public void onKill(PlayerKillEvent event) {
        reward(event.getKiller(), plugin.getConfig().getLong(KILL_PATH, DEFAULT_KILL_REWARD), "kill");
        for (UUID assistant : event.getAssistants()) {
            reward(assistant, plugin.getConfig().getLong(ASSIST_PATH, DEFAULT_ASSIST_REWARD), "assistência");
        }
    }

    private void reward(UUID playerId, long amount, String reason) {
        if (amount <= 0) {
            return;
        }
        economy.deposit(playerId, amount);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.sendActionBar(Component.text("+" + amount + " (" + reason + ")", NamedTextColor.GREEN));
        }
    }
}
