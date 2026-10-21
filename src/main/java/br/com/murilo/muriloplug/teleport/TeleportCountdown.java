package br.com.murilo.muriloplug.teleport;

import br.com.murilo.muriloplug.combat.CombatManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Contagem regressiva de teleporte, compartilhada pelo /home e pelo /tpa: mostra os segundos na action bar
 * e executa a ação no fim. É cancelada se o jogador mudar de bloco, levar dano, bater em outro jogador
 * ou sair do servidor. Cada jogador tem no máximo uma contagem por vez.
 */
public final class TeleportCountdown implements Listener {

    public static final int COUNTDOWN_SECONDS = 5;

    private final JavaPlugin plugin;
    private final Map<UUID, BukkitTask> pending = new HashMap<>();

    public TeleportCountdown(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Inicia a contagem do jogador (substituindo a anterior); {@code onComplete} roda quando ela termina. */
    public void start(Player player, Runnable onComplete) {
        cancel(player);

        UUID id = player.getUniqueId();
        int[] remaining = {COUNTDOWN_SECONDS};
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    pending.remove(id);
                    cancel();
                    return;
                }
                if (remaining[0] == 0) {
                    pending.remove(id);
                    cancel();
                    onComplete.run();
                    return;
                }
                player.sendActionBar(Component.text("Teleportando em " + remaining[0] + "...", NamedTextColor.YELLOW));
                remaining[0]--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
        pending.put(id, task);
    }

    /** Cancela a contagem do jogador, se houver. */
    public boolean cancel(Player player) {
        BukkitTask task = pending.remove(player.getUniqueId());
        if (task == null) {
            return false;
        }
        task.cancel();
        return true;
    }

    public void cancelAll() {
        pending.values().forEach(BukkitTask::cancel);
        pending.clear();
    }

    private void cancelWithMessage(Player player) {
        if (cancel(player)) {
            player.sendActionBar(Component.empty());
            player.sendMessage("§cTeleporte cancelado.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.hasChangedBlock() && pending.containsKey(event.getPlayer().getUniqueId())) {
            cancelWithMessage(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && pending.containsKey(player.getUniqueId())) {
            cancelWithMessage(player);
        }
    }

    /** Bater em outro jogador também cancela a contagem (quem apanha já é cancelado por {@link #onDamage}). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        Player attacker = CombatManager.attackerOf(event);
        if (attacker != null && event.getEntity() instanceof Player victim && !attacker.equals(victim)
                && pending.containsKey(attacker.getUniqueId())) {
            cancelWithMessage(attacker);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer());
    }
}
