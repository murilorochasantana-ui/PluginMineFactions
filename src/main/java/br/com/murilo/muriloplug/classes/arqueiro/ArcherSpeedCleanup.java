package br.com.murilo.muriloplug.classes.arqueiro;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;

/**
 * A velocidade extra com o arco foi removida. Como o AttributeModifier antigo (mineplug:arqueiro_velocidade) era
 * salvo junto com o jogador, esta limpeza o tira de quem está online ao ligar o plugin e de quem entrar.
 */
public final class ArcherSpeedCleanup implements Listener {

    private final NamespacedKey key;

    public ArcherSpeedCleanup(JavaPlugin plugin) {
        this.key = new NamespacedKey(plugin, "arqueiro_velocidade");
    }

    /** Limpa os jogadores online. Chamar no onEnable. */
    public void start() {
        Bukkit.getOnlinePlayers().forEach(this::remove);
    }

    private void remove(Player player) {
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        for (AttributeModifier modifier : new ArrayList<>(speed.getModifiers())) {
            if (key.equals(modifier.getKey())) {
                speed.removeModifier(modifier);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        remove(event.getPlayer());
    }
}
