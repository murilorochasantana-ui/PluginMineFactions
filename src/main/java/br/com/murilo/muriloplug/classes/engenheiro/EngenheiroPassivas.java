package br.com.murilo.muriloplug.classes.engenheiro;

import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.classes.PlayerClassChangeEvent;
import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Passivas e fraquezas de efeito do Engenheiro: Especialista em Explosivos (metade do dano de explosões), Mãos
 * Rápidas (Pressa I com picareta na mão) e a Lentidão da armadura de netherite. Os efeitos são infinitos e sem
 * partículas; são tirados ao sair, morrer e deixar de ser Engenheiro, e conferidos de novo a cada meio segundo
 * (leite, troca de item por outros meios etc.).
 */
public final class EngenheiroPassivas implements Listener {

    private static final long CHECK_TICKS = 10L;

    private final JavaPlugin plugin;
    private final ClassManager classes;
    private final EngenheiroSettings settings;

    public EngenheiroPassivas(JavaPlugin plugin, ClassManager classes, EngenheiroSettings settings) {
        this.plugin = plugin;
        this.classes = classes;
        this.settings = settings;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshAll, CHECK_TICKS, CHECK_TICKS);
        refreshAll();
    }

    /** onDisable: tira os efeitos de todos (eles são salvos junto com o jogador). */
    public void removeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            clear(player);
        }
    }

    private boolean isEngineer(Player player) {
        return classes.hasClass(player.getUniqueId(), PlayerClass.ENGENHEIRO);
    }

    // ------------------------------------------------------------ Especialista em Explosivos

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplosion(EntityDamageEvent event) {
        if ((event.getCause() != DamageCause.ENTITY_EXPLOSION && event.getCause() != DamageCause.BLOCK_EXPLOSION)
                || !(event.getEntity() instanceof Player player) || !isEngineer(player)) {
            return;
        }
        event.setDamage(event.getDamage() * (1 - settings.explosionReduction()));
    }

    // ------------------------------------------------------------ efeitos

    private void refreshAll() {
        Bukkit.getOnlinePlayers().forEach(this::refresh);
    }

    private void refresh(Player player) {
        if (!isEngineer(player)) {
            clear(player);
            return;
        }
        boolean haste = player.getInventory().getItemInMainHand().getType().name().endsWith("_PICKAXE");
        sync(player, PotionEffectType.HASTE, haste ? 0 : -1);
        int pieces = netheritePieces(player);
        sync(player, PotionEffectType.SLOWNESS, pieces == 0 ? -1 : settings.netheriteSlowness(pieces) - 1);
    }

    /** Deixa o efeito com o nível pedido (-1 = sem o efeito). Só tira efeito infinito, que é o nosso. */
    private static void sync(Player player, PotionEffectType type, int amplifier) {
        PotionEffect current = player.getPotionEffect(type);
        boolean ours = current != null && current.isInfinite();
        if (amplifier < 0) {
            if (ours) {
                player.removePotionEffect(type);
            }
            return;
        }
        if (current != null && !ours && current.getAmplifier() >= amplifier) {
            return; // um efeito de outra fonte, igual ou mais forte, já cobre
        }
        if (ours && current.getAmplifier() == amplifier) {
            return;
        }
        player.addPotionEffect(new PotionEffect(type, PotionEffect.INFINITE_DURATION, amplifier, false, false, true));
    }

    private void clear(Player player) {
        sync(player, PotionEffectType.HASTE, -1);
        sync(player, PotionEffectType.SLOWNESS, -1);
    }

    private static int netheritePieces(Player player) {
        int count = 0;
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece != null && isNetherite(piece)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isNetherite(ItemStack item) {
        String name = item.getType().name();
        return name.equals("NETHERITE_HELMET") || name.equals("NETHERITE_CHESTPLATE")
                || name.equals("NETHERITE_LEGGINGS") || name.equals("NETHERITE_BOOTS");
    }

    // ------------------------------------------------------------ eventos

    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent event) {
        Player player = event.getPlayer();
        if (!isEngineer(player)) {
            return;
        }
        ItemStack worn = event.getNewItem();
        if (worn != null && isNetherite(worn)) {
            player.sendMessage("§cArmadura de netherite é pesada demais para você.");
        } else if (worn != null && EngenheiroAcoes.isIron(worn.getType())) {
            player.sendMessage("§eArmadura de ferro pode dar choque nas suas ações.");
        }
        later(player);
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        later(event.getPlayer()); // o slot só muda depois do evento
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        later(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        later(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        later(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        clear(event.getEntity());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer());
    }

    @EventHandler
    public void onClassChange(PlayerClassChangeEvent event) {
        Player player = event.getPlayer();
        if (player != null) {
            refresh(player);
        }
    }

    private void later(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                refresh(player);
            }
        });
    }
}
