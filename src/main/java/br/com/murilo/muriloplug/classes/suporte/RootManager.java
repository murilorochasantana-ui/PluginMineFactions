package br.com.murilo.muriloplug.classes.suporte;

import br.com.murilo.muriloplug.combat.CombatManager;
import br.com.murilo.muriloplug.shop.ShopUi;
import br.com.murilo.muriloplug.stats.DamageTracker;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Estrela do Nether: prende o inimigo na mira por alguns segundos (sem andar nem pular; a câmera gira normal),
 * coloca os dois em combate e conta como participação na morte do preso (assistência).
 */
public final class RootManager implements Listener {

    private static final long TICK_PERIOD = 5L;

    private final JavaPlugin plugin;
    private final SuporteSettings settings;
    private final SuporteTargets targets;
    private final CombatManager combat;
    private final DamageTracker damage;
    /** Fim da prisão (ms) de cada jogador preso. */
    private final Map<UUID, Long> rootedUntil = new HashMap<>();

    public RootManager(JavaPlugin plugin, SuporteSettings settings, SuporteTargets targets, CombatManager combat,
                       DamageTracker damage) {
        this.plugin = plugin;
        this.settings = settings;
        this.targets = targets;
        this.combat = combat;
        this.damage = damage;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
    }

    public boolean isRooted(UUID id) {
        Long until = rootedUntil.get(id);
        return until != null && until > System.currentTimeMillis();
    }

    /** Prende o inimigo na mira. Retorna false (sem gastar nada) se não há alvo válido. */
    public boolean root(Player support) {
        Player target = targets.aimedPlayer(support, settings.rootRange());
        if (target == null) {
            support.sendActionBar(ShopUi.text("§cNenhum alvo na mira."));
            return false;
        }
        if (targets.isAlly(support, target)) {
            support.sendActionBar(ShopUi.text("§cVocê só pode prender inimigos."));
            return false;
        }
        rootedUntil.put(target.getUniqueId(), System.currentTimeMillis() + (long) (settings.rootSeconds() * 1000));
        combat.engage(support, target);
        damage.record(target.getUniqueId(), support.getUniqueId());
        target.sendActionBar(ShopUi.text("§cVocê foi preso por " + support.getName() + "!"));
        support.sendActionBar(ShopUi.text("§aVocê prendeu " + target.getName() + "."));
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1f, 0.8f);
        effects(target);
        return true;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (UUID id : Set.copyOf(rootedUntil.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || rootedUntil.get(id) <= now) {
                rootedUntil.remove(id);
            } else {
                effects(player);
            }
        }
    }

    /** Correntes e raízes em volta dos pés. */
    private void effects(Player target) {
        Location feet = target.getLocation();
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            Location point = feet.clone().add(Math.cos(angle) * 0.6, 0.3 + (i % 2) * 0.6, Math.sin(angle) * 0.6);
            target.getWorld().spawnParticle(Particle.BLOCK, point, 2, 0.05, 0.1, 0.05, 0,
                    Material.CHAIN.createBlockData());
        }
        target.getWorld().spawnParticle(Particle.DUST, feet.clone().add(0, 0.1, 0), 6, 0.4, 0.05, 0.4, 0,
                new Particle.DustOptions(Color.fromRGB(70, 110, 40), 1.0f));
    }

    /** Preso não anda nem pula (a câmera pode girar). Cair continua possível. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Long until = rootedUntil.get(player.getUniqueId());
        if (until == null) {
            return;
        }
        if (until <= System.currentTimeMillis()) {
            rootedUntil.remove(player.getUniqueId());
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to.getX() == from.getX() && to.getY() == from.getY() && to.getZ() == from.getZ()) {
            return;
        }
        Location fixed = to.clone();
        fixed.setX(from.getX());
        fixed.setZ(from.getZ());
        if (to.getY() >= from.getY()) {
            fixed.setY(from.getY()); // sem pular; só descer (cair) é permitido
        }
        event.setTo(fixed);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        rootedUntil.remove(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        rootedUntil.remove(event.getPlayer().getUniqueId());
    }
}
