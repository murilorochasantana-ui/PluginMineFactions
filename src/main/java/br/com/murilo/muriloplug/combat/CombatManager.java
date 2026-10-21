package br.com.murilo.muriloplug.combat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Controla quem está em combate. Um jogador entra em combate ao bater ou apanhar de outro jogador
 * (golpe direto ou projétil) e sai {@link #COMBAT_SECONDS} segundos depois do último golpe.
 */
public final class CombatManager implements Listener {

    public static final int COMBAT_SECONDS = 5;

    /** Momento (ms) em que o combate de cada jogador termina. A entrada só some quando {@link #collectExpired()} a vê. */
    private final Map<UUID, Long> combatUntil = new HashMap<>();
    /** Momento (ms) em que o último combate de cada jogador terminou (ou termina). Fica até o jogador sair. */
    private final Map<UUID, Long> lastCombatEnd = new HashMap<>();
    private Consumer<Player> enterListener = player -> { };

    /**
     * Segundos (arredondados para cima) até passar {@code waitSeconds} desde o fim do último combate do jogador,
     * contando o combate atual se ele ainda estiver em combate. 0 = já pode.
     */
    public int secondsUntilCalm(Player player, int waitSeconds) {
        Long end = lastCombatEnd.get(player.getUniqueId());
        if (end == null) {
            return 0;
        }
        long remaining = end + waitSeconds * 1000L - System.currentTimeMillis();
        return remaining <= 0 ? 0 : (int) Math.ceil(remaining / 1000.0);
    }

    /** Chamado quando um jogador passa de "fora de combate" para "em combate". */
    public void setEnterListener(Consumer<Player> enterListener) {
        this.enterListener = enterListener;
    }

    /** Jogador que causou o dano (golpe direto ou projétil atirado por jogador), ou null. */
    public static Player attackerOf(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    public boolean isInCombat(Player player) {
        Long until = combatUntil.get(player.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    /** Segundos restantes de combate (arredondados para cima), ou 0 se o jogador não está em combate. */
    public int remainingSeconds(Player player) {
        Long until = combatUntil.get(player.getUniqueId());
        if (until == null) {
            return 0;
        }
        long remainingMillis = until - System.currentTimeMillis();
        return remainingMillis <= 0 ? 0 : (int) Math.ceil(remainingMillis / 1000.0);
    }

    /** Remove e devolve os jogadores online cujo combate terminou desde a última chamada. */
    public List<Player> collectExpired() {
        long now = System.currentTimeMillis();
        List<Player> expired = new ArrayList<>();
        Iterator<Map.Entry<UUID, Long>> iterator = combatUntil.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            if (entry.getValue() <= now) {
                iterator.remove();
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null) {
                    expired.add(player);
                }
            }
        }
        return expired;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attackerOf(event);
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        mark(attacker);
        mark(victim);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        combatUntil.remove(event.getPlayer().getUniqueId());
        lastCombatEnd.remove(event.getPlayer().getUniqueId());
    }

    private void mark(Player player) {
        boolean wasInCombat = combatUntil.containsKey(player.getUniqueId());
        long until = System.currentTimeMillis() + COMBAT_SECONDS * 1000L;
        combatUntil.put(player.getUniqueId(), until);
        lastCombatEnd.put(player.getUniqueId(), until);
        if (!wasInCombat) {
            enterListener.accept(player);
        }
    }
}
