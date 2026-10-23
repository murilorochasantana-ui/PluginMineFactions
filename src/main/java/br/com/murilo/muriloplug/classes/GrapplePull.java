package br.com.murilo.muriloplug.classes;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A puxada dos ganchos (Flecha Gancho do Engenheiro e Flecha de Corda do Arqueiro): leva o jogador até o ponto de
 * impacto com uma linha de partículas e o protege do dano de queda durante a puxada e por alguns segundos depois.
 */
public final class GrapplePull implements Listener {

    private static final int PULL_MAX_TICKS = 40;
    private static final double ARRIVAL_DISTANCE = 1.5;

    private final JavaPlugin plugin;
    /** Fim (ms) da proteção contra queda. */
    private final Map<UUID, Long> fallProtectedUntil = new HashMap<>();

    public GrapplePull(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Um pouco para fora da parede (e mais alto no chão), para o corpo do jogador caber no ponto. */
    public static Location target(Location impact, BlockFace face) {
        Location target = impact.clone();
        if (face != null) {
            target.add(face.getDirection().multiply(0.6));
            if (face == BlockFace.UP) {
                target.add(0, 0.9, 0);
            }
        }
        return target;
    }

    /** Puxa o jogador até o ponto. {@code fallProtectionSeconds}: sem dano de queda por este tempo depois da puxada. */
    public void pull(Player player, Location target, double speed, int fallProtectionSeconds) {
        UUID id = player.getUniqueId();
        World world = player.getWorld();
        fallProtectedUntil.put(id, Long.MAX_VALUE);
        world.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 0.8f);

        new BukkitRunnable() {
            int elapsed;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || !player.getWorld().equals(world)
                        || elapsed++ >= PULL_MAX_TICKS) {
                    finish();
                    return;
                }
                Location from = player.getBoundingBox().getCenter().toLocation(world);
                Vector toTarget = target.toVector().subtract(from.toVector());
                double distance = toTarget.length();
                if (distance <= ARRIVAL_DISTANCE) {
                    player.setVelocity(player.getVelocity().multiply(0.3));
                    finish();
                    return;
                }
                player.setVelocity(toTarget.clone().normalize().multiply(Math.min(speed, distance)).add(new Vector(0, 0.04, 0)));
                line(from, target, distance);
            }

            private void finish() {
                cancel();
                // Sem dano de queda também por alguns segundos depois da puxada.
                fallProtectedUntil.put(id, System.currentTimeMillis() + fallProtectionSeconds * 1000L);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Linha de partículas entre o jogador e o ponto de impacto. */
    private static void line(Location from, Location to, double distance) {
        Vector step = to.toVector().subtract(from.toVector()).normalize().multiply(0.5);
        Location point = from.clone();
        for (int i = 0; i < Math.min(60, (int) (distance / 0.5)); i++) {
            point.add(step);
            from.getWorld().spawnParticle(Particle.END_ROD, point, 1, 0, 0, 0, 0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != DamageCause.FALL || !(event.getEntity() instanceof Player player)) {
            return;
        }
        Long until = fallProtectedUntil.get(player.getUniqueId());
        if (until != null && until > System.currentTimeMillis()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        fallProtectedUntil.remove(event.getPlayer().getUniqueId());
    }
}
