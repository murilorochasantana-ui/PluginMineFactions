package br.com.murilo.muriloplug.classes.engenheiro;

import br.com.murilo.muriloplug.auth.AuthManager;
import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Passiva Reparo de itens: Shift + clique direito no ar com um item danificado (ferramenta, arma ou armadura)
 * conserta por completo, gastando energia. Itens de classe (marcados com chaves do próprio plugin) e itens sem
 * durabilidade não podem ser consertados.
 */
public final class EngenheiroReparo implements Listener {

    private static final long DEBOUNCE_MILLIS = 300L;

    private final ClassManager classes;
    private final EngenheiroSettings settings;
    private final EngenheiroEnergia energy;
    private final EngenheiroAcoes actions;
    private final AuthManager auth;
    private final String pluginNamespace;
    private final Map<UUID, Long> lastUse = new HashMap<>();

    public EngenheiroReparo(JavaPlugin plugin, ClassManager classes, EngenheiroSettings settings,
                            EngenheiroEnergia energy, EngenheiroAcoes actions, AuthManager auth) {
        this.classes = classes;
        this.settings = settings;
        this.energy = energy;
        this.actions = actions;
        this.auth = auth;
        this.pluginNamespace = new NamespacedKey(plugin, "x").getNamespace();
    }

    /** Item com durabilidade que não é um item de classe. */
    private boolean isRepairable(ItemStack item) {
        if (item == null || item.getType().getMaxDurability() <= 0) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable)) {
            return false;
        }
        for (NamespacedKey key : meta.getPersistentDataContainer().getKeys()) {
            if (key.getNamespace().equals(pluginNamespace)) {
                return false; // item de classe (usos limitados)
            }
        }
        return true;
    }

    /** Itens que se usam agachado (mirar com o arco etc.): sem aviso quando não há o que consertar. */
    private static boolean isUsedWhileSneaking(ItemStack item) {
        return switch (item.getType()) {
            case BOW, CROSSBOW, TRIDENT, SHIELD, FISHING_ROD -> true;
            default -> false;
        };
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getAction() != Action.RIGHT_CLICK_AIR || event.getHand() != EquipmentSlot.HAND
                || !player.isSneaking() || !classes.hasClass(player.getUniqueId(), PlayerClass.ENGENHEIRO)) {
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().getMaxDurability() <= 0) {
            return;
        }
        UUID id = player.getUniqueId();
        if (item.getType().getEquipmentSlot().isArmor()) {
            event.setUseItemInHand(Event.Result.DENY); // o clique é do Reparo: não equipa a armadura
        }
        long now = System.currentTimeMillis();
        Long last = lastUse.get(id);
        if ((last != null && now - last < DEBOUNCE_MILLIS) || !auth.isLoggedIn(id)) {
            return;
        }
        lastUse.put(id, now);

        if (!isRepairable(item)) {
            if (!isUsedWhileSneaking(item)) {
                player.sendMessage("§cEste item não pode ser consertado.");
            }
            return;
        }
        Damageable meta = (Damageable) item.getItemMeta();
        if (meta.getDamage() <= 0) {
            if (!isUsedWhileSneaking(item)) {
                player.sendMessage("§eEste item não está danificado.");
            }
            return;
        }
        double cost = settings.repairCost();
        if (!energy.temEnergia(id, cost)) {
            player.sendMessage("§cEnergia insuficiente (" + (int) energy.getEnergia(id) + "/" + (int) cost + ").");
            return;
        }
        energy.gastarEnergia(id, cost);
        meta.setDamage(0);
        item.setItemMeta(meta);
        player.getInventory().setItemInMainHand(item);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
        player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 1.2, 0), 25, 0.4, 0.4, 0.4, 0.3);
        player.sendMessage("§aItem consertado! §7(-" + (int) cost + " de energia)");
        actions.registrarAcao(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastUse.remove(event.getPlayer().getUniqueId());
    }
}
