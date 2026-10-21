package br.com.murilo.muriloplug.pvp;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Tira a recarga de ataque: com a velocidade de ataque base em 1024, a barra enche na hora e todo golpe sai com
 * dano cheio. O valor base é salvo junto com o jogador, então é devolvido ao padrão (4.0) ao sair, ao desligar o
 * plugin e quando a opção está desligada. Só mexe no valor se ele for o nosso (1024), para não desfazer ajustes
 * de outros plugins.
 */
public final class AttackSpeedManager implements Listener {

    static final double NO_COOLDOWN_SPEED = 1024.0;
    static final double DEFAULT_SPEED = 4.0;

    private final JavaPlugin plugin;
    private final boolean enabled;

    public AttackSpeedManager(JavaPlugin plugin, boolean enabled) {
        this.plugin = plugin;
        this.enabled = enabled;
    }

    /** Aplica (ou, com a opção desligada, limpa) o valor em todos os jogadores online. Chamar no onEnable. */
    public void applyToOnlinePlayers() {
        Bukkit.getOnlinePlayers().forEach(this::apply);
    }

    /** Devolve o valor padrão a todos os jogadores online. Chamar no onDisable. */
    public void restoreOnlinePlayers() {
        Bukkit.getOnlinePlayers().forEach(this::restore);
    }

    private void apply(Player player) {
        AttributeInstance speed = player.getAttribute(Attribute.ATTACK_SPEED);
        if (speed == null) {
            return;
        }
        if (enabled) {
            speed.setBaseValue(NO_COOLDOWN_SPEED);
        } else if (speed.getBaseValue() == NO_COOLDOWN_SPEED) {
            speed.setBaseValue(DEFAULT_SPEED);
        }
    }

    private void restore(Player player) {
        AttributeInstance speed = player.getAttribute(Attribute.ATTACK_SPEED);
        if (speed != null && speed.getBaseValue() == NO_COOLDOWN_SPEED) {
            speed.setBaseValue(DEFAULT_SPEED);
        }
    }

    /** Aplica agora e de novo no tick seguinte, quando o jogador já está totalmente no mundo. */
    private void applyNowAndNextTick(Player player) {
        apply(player);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                apply(player);
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        applyNowAndNextTick(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        applyNowAndNextTick(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        applyNowAndNextTick(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        restore(event.getPlayer());
    }
}
