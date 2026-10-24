package br.com.murilo.muriloplug.classes.suporte;

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
 * Vida máxima extra do Suporte (AttributeModifier mineplug:suporte_vida, +16 = 18 corações). A chave é a mesma da
 * antiga fraqueza (-4), então quem ainda tiver o modificador antigo recebe o novo no lugar. Não cura ao colocar. Como
 * o modificador é salvo junto com o jogador, ele é removido ao sair, ao desligar o plugin e ao trocar de classe.
 */
public final class SuporteHealth implements Listener {

    private final JavaPlugin plugin;
    private final ClassManager classes;
    private final SuporteSettings settings;
    private final NamespacedKey key;

    public SuporteHealth(JavaPlugin plugin, ClassManager classes, SuporteSettings settings) {
        this.plugin = plugin;
        this.classes = classes;
        this.settings = settings;
        this.key = new NamespacedKey(plugin, "suporte_vida");
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
        if (!classes.hasClass(player.getUniqueId(), PlayerClass.SUPORTE) || extra <= 0) {
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
        if (player.getHealth() > max.getValue()) {
            player.setHealth(max.getValue());
        }
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
