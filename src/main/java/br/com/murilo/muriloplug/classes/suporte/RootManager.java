package br.com.murilo.muriloplug.classes.suporte;

import br.com.murilo.muriloplug.combat.CombatManager;
import br.com.murilo.muriloplug.shop.ShopUi;
import br.com.murilo.muriloplug.stats.DamageTracker;
import br.com.murilo.muriloplug.teste.StatusRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

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
    private final StatusRegistry statuses;
    /** Fim da prisão (ms) de cada jogador preso. */
    private final Map<UUID, Long> rootedUntil = new HashMap<>();

    public RootManager(JavaPlugin plugin, SuporteSettings settings, SuporteTargets targets, CombatManager combat,
                       DamageTracker damage, StatusRegistry statuses) {
        this.statuses = statuses;
        this.plugin = plugin;
        this.settings = settings;
        this.targets = targets;
        this.combat = combat;
        this.damage = damage;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::freezeMobs, 1L, 1L);
    }

    /** Quem não é jogador (o boneco de teste) não anda enquanto preso: o movimento horizontal é zerado a cada tick. */
    private void freezeMobs() {
        if (rootedUntil.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> entry : rootedUntil.entrySet()) {
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity != null && !(entity instanceof Player) && entity.isValid() && entry.getValue() > now) {
                Vector velocity = entity.getVelocity();
                entity.setVelocity(new Vector(0, Math.min(velocity.getY(), 0), 0));
            }
        }
    }

    public boolean isRooted(UUID id) {
        Long until = rootedUntil.get(id);
        return until != null && until > System.currentTimeMillis();
    }

    /** Prende o inimigo na mira. Retorna false (sem gastar nada) se não há alvo válido. */
    public boolean root(Player support) {
        LivingEntity target = targets.aimedTarget(support, settings.rootRange());
        if (target == null) {
            support.sendActionBar(ShopUi.text("§cNenhum alvo na mira."));
            return false;
        }
        if (targets.isAlly(support, target)) {
            support.sendActionBar(ShopUi.text("§cVocê só pode prender inimigos."));
            return false;
        }
        hold(target, settings.rootSeconds());
        if (target instanceof Player player) {
            combat.engage(support, player);
            damage.record(player.getUniqueId(), support.getUniqueId());
            player.sendActionBar(ShopUi.text("§cVocê foi preso por " + support.getName() + "!"));
            support.sendActionBar(ShopUi.text("§aVocê prendeu " + player.getName() + "."));
        } else {
            combat.engage(support);
            support.sendActionBar(ShopUi.text("§aVocê prendeu o alvo."));
        }
        return true;
    }

    /** Prende a entidade (sem andar nem pular) pelo tempo dado, com o status "Preso" e as correntes. */
    public void hold(LivingEntity target, double seconds) {
        rootedUntil.put(target.getUniqueId(), System.currentTimeMillis() + (long) (seconds * 1000));
        statuses.add(target, "Preso", seconds);
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1f, 0.8f);
        effects(target);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (UUID id : Set.copyOf(rootedUntil.keySet())) {
            Entity entity = Bukkit.getEntity(id);
            if (entity == null || !entity.isValid() || rootedUntil.get(id) <= now) {
                rootedUntil.remove(id);
            } else if (entity instanceof LivingEntity living) {
                effects(living);
            }
        }
    }

    /** Correntes e raízes em volta dos pés. */
    private void effects(LivingEntity target) {
        Location feet = target.getLocation();
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            Location point = feet.clone().add(Math.cos(angle) * 0.6, 0.3 + (i % 2) * 0.6, Math.sin(angle) * 0.6);
            target.getWorld().spawnParticle(Particle.BLOCK, point, 2, 0.05, 0.1, 0.05, 0,
                    Material.IRON_CHAIN.createBlockData());
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
