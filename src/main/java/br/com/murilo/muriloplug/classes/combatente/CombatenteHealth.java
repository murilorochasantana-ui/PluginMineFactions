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
 * Atributos do Combatente: vida máxima extra (mineplug:combatente_vida) e a passiva Inabalável, resistência a
 * empurrão (mineplug:combatente_inabalavel). Não cura ao colocar. Como os modificadores são salvos junto com o
 * jogador, eles são removidos ao sair, ao desligar o plugin e ao trocar de classe.
 */
public final class CombatenteHealth implements Listener {

    private final JavaPlugin plugin;
    private final ClassManager classes;
    private final CombatenteSettings settings;
    private final NamespacedKey healthKey;
    private final NamespacedKey steadyKey;

    public CombatenteHealth(JavaPlugin plugin, ClassManager classes, CombatenteSettings settings) {
        this.plugin = plugin;
        this.classes = classes;
        this.settings = settings;
        this.healthKey = new NamespacedKey(plugin, "combatente_vida");
        this.steadyKey = new NamespacedKey(plugin, "combatente_inabalavel");
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
        boolean combatente = classes.hasClass(player.getUniqueId(), PlayerClass.COMBATENTE);
        double extra = settings.extraHealth();
        if (combatente && extra > 0) {
            apply(player, Attribute.MAX_HEALTH, healthKey, extra);
        } else {
            remove(player, Attribute.MAX_HEALTH, healthKey);
        }
        double steady = settings.knockbackResistance();
        if (combatente && steady > 0) {
            apply(player, Attribute.KNOCKBACK_RESISTANCE, steadyKey, steady);
        } else {
            remove(player, Attribute.KNOCKBACK_RESISTANCE, steadyKey);
        }
    }

    private void apply(Player player, Attribute attribute, NamespacedKey key, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier current = find(instance, key);
        if (current != null && current.getAmount() == amount) {
            return;
        }
        if (current != null) {
            instance.removeModifier(current);
        }
        instance.addModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_NUMBER));
    }

    private void remove(Player player) {
        remove(player, Attribute.MAX_HEALTH, healthKey);
        remove(player, Attribute.KNOCKBACK_RESISTANCE, steadyKey);
    }

    /** Tira o modificador e, se a vida atual passar da nova máxima, baixa para a máxima. */
    private void remove(Player player, Attribute attribute, NamespacedKey key) {
        AttributeInstance instance = player.getAttribute(attribute);
        AttributeModifier current = instance == null ? null : find(instance, key);
        if (current == null) {
            return;
        }
        instance.removeModifier(current);
        if (attribute == Attribute.MAX_HEALTH && player.getHealth() > instance.getValue()) {
            player.setHealth(instance.getValue());
        }
    }

    private static AttributeModifier find(AttributeInstance instance, NamespacedKey key) {
        for (AttributeModifier modifier : new ArrayList<>(instance.getModifiers())) {
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
