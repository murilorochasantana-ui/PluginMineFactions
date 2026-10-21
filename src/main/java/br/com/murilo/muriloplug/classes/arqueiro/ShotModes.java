package br.com.murilo.muriloplug.classes.arqueiro;

import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.classes.PlayerClassChangeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Modo de tiro (1 flecha / rajada de 3) e recarga da rajada de cada Arqueiro, só em memória. */
public final class ShotModes implements Listener {

    private final Set<UUID> burst = new HashSet<>();
    /** Momento (ms) em que a recarga da rajada de cada jogador termina. */
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();
    private Consumer<UUID> changeListener = id -> { };

    public void setChangeListener(Consumer<UUID> changeListener) {
        this.changeListener = changeListener;
    }

    /** true = rajada de 3; false = 1 flecha (padrão). */
    public boolean isBurst(UUID player) {
        return burst.contains(player);
    }

    /** Alterna o modo e devolve true se agora é rajada. */
    public boolean toggle(UUID player) {
        boolean nowBurst = burst.add(player);
        if (!nowBurst) {
            burst.remove(player);
        }
        changeListener.accept(player);
        return nowBurst;
    }

    public void startCooldown(UUID player, double seconds) {
        if (seconds > 0) {
            cooldownUntil.put(player, System.currentTimeMillis() + (long) (seconds * 1000));
        } else {
            cooldownUntil.remove(player);
        }
    }

    /** Segundos restantes da recarga (arredondados para cima), ou 0 se não há recarga. */
    public int cooldownRemainingSeconds(UUID player) {
        Long until = cooldownUntil.get(player);
        if (until == null) {
            return 0;
        }
        long remaining = until - System.currentTimeMillis();
        if (remaining <= 0) {
            cooldownUntil.remove(player);
            return 0;
        }
        return (int) Math.ceil(remaining / 1000.0);
    }

    private void forget(UUID player) {
        burst.remove(player);
        cooldownUntil.remove(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        forget(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onClassChange(PlayerClassChangeEvent event) {
        if (event.getOldClass() == PlayerClass.ARQUEIRO || event.getNewClass() == PlayerClass.ARQUEIRO) {
            forget(event.getPlayerId());
        }
    }
}
