package br.com.murilo.muriloplug.classes.combatente;

import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.classes.PlayerClassChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;

/**
 * Vida máxima extra do Combatente (AttributeModifier mineplug:combatente_vida). Não cura ao colocar. Como o
 * modificador é salvo junto com o jogador, ele é removido ao sair, ao desligar o plugin e ao trocar de classe.
 */
public final class CombatenteHealth implements Listener {

    private final JavaPlugin plugin;
    private final ClassManager classes;
    private final CombatenteSettings settings;
    private final NamespacedKey key;

    public CombatenteHealth(JavaPlugin plugin, ClassManager classes, CombatenteSettings settings) {
        this.plugin = plugin;
        this.classes = classes;
        this.settings = settings;
        this.key = new NamespacedKey(plugin, "combatente_vida");
    }

    /** Aplica nos jogadores online. Chamar no onEnable. */
    public void applyOnline() {
        Bukkit.getOnlinePlayers().forEach(this::refresh);
    }

    /** Remove de todos os jogadores online. Chamar no onDisable. */
    public void removeAll() {
        Bukkit.getOnlinePlayers().forEach(this::remove);
    }

    private void refresh(Player player) {
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        if (max == null) {
            return;
        }
        double extra = settings.extraHealth();
        if (!classes.hasClass(player.getUniqueId(), PlayerClass.COMBATENTE) || extra <= 0) {
            remove(player);
            return;
        }
        AttributeModifier current = find(max);
        if (current != null && current.getAmount() == extra) {
            return;
        }
        if (current != null) {
            max.removeModifier(current);
        }
        max.addModifier(new AttributeModifier(key, extra, AttributeModifier.Operation.ADD_NUMBER));
    }

    /** Tira o modificador e, se a vida atual passar da nova máxima, baixa para a máxima. */
    private void remove(Player player) {
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        AttributeModifier current = max == null ? null : find(max);
        if (current == null) {
            return;
        }
        max.removeModifier(current);
        if (player.getHealth() > max.getValue()) {
            player.setHealth(max.getValue());
        }
    }

    private AttributeModifier find(AttributeInstance max) {
        for (AttributeModifier modifier : new ArrayList<>(max.getModifiers())) {
            if (key.equals(modifier.getKey())) {
                return modifier;
            }
        }
        return null;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        refresh(player);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                refresh(player);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        remove(event.getPlayer());
    }

    @EventHandler
    public void onClassChange(PlayerClassChangeEvent event) {
        Player player = event.getPlayer();
        if (player != null) {
            refresh(player);
        }
    }
}
