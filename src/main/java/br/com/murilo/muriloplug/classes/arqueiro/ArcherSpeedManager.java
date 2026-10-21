package br.com.murilo.muriloplug.classes.arqueiro;

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
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;

/**
 * +20% de velocidade de movimento enquanto o Arqueiro segura o arco na mão principal. Usa um AttributeModifier com
 * chave própria (mineplug:arqueiro_velocidade), não efeito de poção. Como o modificador é salvo junto com o jogador,
 * ele é removido ao sair e ao desligar o plugin; uma varredura a cada segundo cobre o que nenhum evento avisa.
 */
public final class ArcherSpeedManager implements Listener {

    private final JavaPlugin plugin;
    private final ClassManager classes;
    private final ArqueiroSettings settings;
    private final NamespacedKey key;

    public ArcherSpeedManager(JavaPlugin plugin, ClassManager classes, ArqueiroSettings settings) {
        this.plugin = plugin;
        this.classes = classes;
        this.settings = settings;
        this.key = new NamespacedKey(plugin, "arqueiro_velocidade");
    }

    /** Aplica agora nos jogadores online e inicia a varredura de segurança. Chamar no onEnable. */
    public void start() {
        Bukkit.getOnlinePlayers().forEach(this::refresh);
        plugin.getServer().getScheduler().runTaskTimer(plugin,
                () -> Bukkit.getOnlinePlayers().forEach(this::refresh), 20L, 20L);
    }

    /** Remove o modificador de todos os jogadores online. Chamar no onDisable. */
    public void removeAll() {
        Bukkit.getOnlinePlayers().forEach(this::remove);
    }

    /** Põe o modificador se o jogador é Arqueiro com o arco na mão principal; senão tira. */
    private void refresh(Player player) {
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        double bonus = settings.speedBonus();
        boolean wanted = bonus > 0 && classes.hasClass(player.getUniqueId(), PlayerClass.ARQUEIRO)
                && settings.isArcherBow(player.getInventory().getItemInMainHand());
        AttributeModifier current = find(speed);
        if (!wanted) {
            if (current != null) {
                speed.removeModifier(current);
            }
            return;
        }
        if (current != null && current.getAmount() == bonus) {
            return;
        }
        if (current != null) {
            speed.removeModifier(current);
        }
        speed.addModifier(new AttributeModifier(key, bonus, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }

    private void remove(Player player) {
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        AttributeModifier current = speed == null ? null : find(speed);
        if (current != null) {
            speed.removeModifier(current);
        }
    }

    private AttributeModifier find(AttributeInstance speed) {
        for (AttributeModifier modifier : new ArrayList<>(speed.getModifiers())) {
            if (key.equals(modifier.getKey())) {
                return modifier;
            }
        }
        return null;
    }

    /** Reavalia no tick seguinte, quando o inventário já está no estado final. */
    private void refreshLater(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                refresh(player);
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer());
        refreshLater(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        refreshLater(event.getPlayer());
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

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        refreshLater(event.getPlayer());
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        refreshLater(event.getPlayer());
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        refreshLater(event.getPlayer());
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            refreshLater(player);
        }
    }

    @EventHandler
    public void onBreak(PlayerItemBreakEvent event) {
        refreshLater(event.getPlayer());
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            refreshLater(player);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            refreshLater(player);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            refreshLater(player);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            refreshLater(player);
        }
    }
}
