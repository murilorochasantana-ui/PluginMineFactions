package br.com.murilo.muriloplug.classes.arqueiro;

import br.com.murilo.muriloplug.classes.SilenceManager;
import br.com.murilo.muriloplug.auth.AuthManager;
import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.classes.PlayerClassChangeEvent;
import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Ultra Foco: a tecla Q com o arco arma o próximo tiro. Se o arco for segurado puxado pelo tempo mínimo, o tiro
 * gasta o Foco e dá o dano multiplicado; se soltar antes, o tiro é normal e o Ultra Foco continua armado (sem gasto).
 * O tempo puxado vem de {@link Player#getActiveItemUsedTime()}; uma tarefa por tick guarda o último valor lido como
 * reserva, caso a API já venha zerada no momento da soltura, e mostra a carga na action bar.
 */
public final class UltraFocus implements Listener {

    private static final String ARMED_BAR = "§6§lULTRA FOCO ARMADO";
    private static final int BAR_SEGMENTS = 10;
    private static final long MAX_TRAIL_TICKS = 200L;

    private final JavaPlugin plugin;
    private final ClassManager classes;
    private final ArqueiroSettings settings;
    private final FocusManager focus;
    private final AuthManager auth;
    private final Set<UUID> armed = new HashSet<>();
    /** Último tempo puxado (ticks) lido de cada Arqueiro que está com o arco esticado. */
    private final Map<UUID, Integer> charge = new HashMap<>();
    private Consumer<UUID> changeListener = id -> { };
    private int ticks;

    public UltraFocus(JavaPlugin plugin, ClassManager classes, ArqueiroSettings settings, FocusManager focus,
                      AuthManager auth) {
        this.plugin = plugin;
        this.classes = classes;
        this.settings = settings;
        this.focus = focus;
        this.auth = auth;
    }

    /** Chamado quando o Ultra Foco é armado ou desarmado (sidebar). */
    public void setChangeListener(Consumer<UUID> changeListener) {
        this.changeListener = changeListener;
    }

    public boolean isArmed(UUID id) {
        return armed.contains(id);
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void disarm(UUID id) {
        charge.remove(id);
        if (armed.remove(id)) {
            changeListener.accept(id);
        }
    }

    // ------------------------------------------------------------ tiro

    /** Ticks que o Arqueiro está com o arco puxado (a API, ou o último valor lido se a API já veio zerada). */
    public int pulledTicks(Player player) {
        return Math.max(player.getActiveItemUsedTime(), charge.getOrDefault(player.getUniqueId(), 0));
    }

    /**
     * Chamado quando o Arqueiro solta o arco. Se o Ultra Foco está armado e o arco ficou puxado pelo tempo mínimo,
     * gasta o Foco, desarma e devolve true (o tiro é Ultra Foco). Senão devolve false e nada muda.
     */
    public boolean tryFire(Player player) {
        UUID id = player.getUniqueId();
        if (!armed.contains(id)) {
            return false;
        }
        int pulled = pulledTicks(player);
        charge.remove(id);
        if (pulled < settings.ultraMinTicks()) {
            return false;
        }
        if (!focus.spend(id, settings.ultraCost())) {
            focus.warnInsufficient(player, settings.ultraCost());
            return false;
        }
        armed.remove(id);
        changeListener.accept(id);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.8f, 1.6f);
        player.getWorld().spawnParticle(Particle.FLASH, player.getEyeLocation(), 1, Color.fromRGB(255, 200, 80));
        return true;
    }

    /** Rastro de partículas da flecha do Ultra Foco, até ela acertar algo. */
    public void trail(AbstractArrow arrow) {
        new BukkitRunnable() {
            long age;

            @Override
            public void run() {
                if (!arrow.isValid() || arrow.isInBlock() || age++ > MAX_TRAIL_TICKS) {
                    cancel();
                    return;
                }
                arrow.getWorld().spawnParticle(Particle.END_ROD, arrow.getLocation(), 3, 0.05, 0.05, 0.05, 0.01);
                arrow.getWorld().spawnParticle(Particle.FLAME, arrow.getLocation(), 2, 0.05, 0.05, 0.05, 0.01);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ------------------------------------------------------------ Q: armar e desarmar

    /** Q com o arco na mão principal: não solta o arco; arma (ou desarma) o Ultra Foco. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (!classes.hasClass(id, PlayerClass.ARQUEIRO)
                || !settings.isArcherBow(event.getItemDrop().getItemStack())
                || !settings.isArcherBow(player.getInventory().getItemInMainHand())
                        && !player.getInventory().getItemInMainHand().getType().isAir()
                || !player.getItemOnCursor().getType().isAir()) {
            return;
        }
        event.setCancelled(true);
        if (!auth.isLoggedIn(id) || SilenceManager.blocks(player)) {
            return;
        }
        if (armed.remove(id)) {
            changeListener.accept(id);
            focus.warn(player, "§7Ultra Foco desarmado");
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.8f);
            return;
        }
        int cost = settings.ultraCost();
        if (!focus.has(id, cost)) {
            focus.warnInsufficient(player, cost);
            return;
        }
        armed.add(id);
        changeListener.accept(id);
        focus.warn(player, ARMED_BAR);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.5f);
        ring(player);
    }

    // ------------------------------------------------------------ relógio

    private void tick() {
        ticks++;
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (!classes.hasClass(id, PlayerClass.ARQUEIRO)) {
                continue;
            }
            boolean drawing = player.isHandRaised() && player.getActiveItem().getType() == Material.BOW
                    && settings.isArcherBow(player.getActiveItem());
            if (!drawing) {
                charge.remove(id);
            } else {
                int pulled = player.getActiveItemUsedTime();
                charge.put(id, pulled);
                if (armed.contains(id) && ticks % 2 == 0) {
                    showCharge(player, pulled);
                }
            }
            if (armed.contains(id)) {
                if (ticks % 5 == 0) {
                    ring(player);
                }
                if (!drawing && ticks % 20 == 0 && !focus.warnedRecently(id)) {
                    player.sendActionBar(ShopUi.text(ARMED_BAR));
                }
            }
        }
    }

    private void showCharge(Player player, int pulled) {
        int needed = settings.ultraMinTicks();
        if (pulled >= needed) {
            player.sendActionBar(ShopUi.text("§6§lULTRA FOCO §a§lPRONTO! §7Solte!"));
            return;
        }
        int filled = pulled * BAR_SEGMENTS / needed;
        String bar = "§e" + "█".repeat(filled) + "§8" + "█".repeat(BAR_SEGMENTS - filled);
        player.sendActionBar(ShopUi.text("§6Ultra Foco " + bar + " §f" + (pulled * 100 / needed) + "%"));
    }

    /** Anel de partículas em volta do Arqueiro armado. */
    private void ring(Player player) {
        double turn = ticks * 0.3;
        for (int i = 0; i < 6; i++) {
            double angle = turn + i * Math.PI / 3;
            player.getWorld().spawnParticle(Particle.FLAME,
                    player.getLocation().add(Math.cos(angle) * 0.8, 0.2 + (i % 3) * 0.7, Math.sin(angle) * 0.8),
                    1, 0, 0, 0, 0);
        }
    }

    // ------------------------------------------------------------ limpeza

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        disarm(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        disarm(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onClassChange(PlayerClassChangeEvent event) {
        if (event.getOldClass() == PlayerClass.ARQUEIRO) {
            disarm(event.getPlayerId());
        }
    }
}
