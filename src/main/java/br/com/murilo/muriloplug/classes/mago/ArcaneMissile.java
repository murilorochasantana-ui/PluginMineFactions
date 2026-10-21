package br.com.murilo.muriloplug.classes.mago;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Míssil Arcano: o ataque básico do Mago. Projétil só de partículas, rápido, que para no primeiro alvo ou bloco
 * sólido, causa dano mágico e devolve mana a cada acerto. Não gasta mana nem tem recarga (só o intervalo entre tiros,
 * controlado pelo {@link StaffService}).
 */
final class ArcaneMissile {

    private final JavaPlugin plugin;
    private final SpellTargets targets;
    private final ManaManager mana;
    private final MagoSettings settings;

    ArcaneMissile(JavaPlugin plugin, SpellTargets targets, ManaManager mana, MagoSettings settings) {
        this.plugin = plugin;
        this.targets = targets;
        this.mana = mana;
        this.settings = settings;
    }

    void fire(Player mage) {
        World world = mage.getWorld();
        Location position = mage.getEyeLocation();
        Vector direction = position.getDirection().normalize();
        double range = settings.missileRange();
        double speed = settings.missileSpeed();
        double damage = settings.missileDamage();
        int manaPerHit = settings.missileManaPerHit();
        world.playSound(position, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.8f);

        new BukkitRunnable() {
            double travelled = 0;

            @Override
            public void run() {
                if (!mage.isOnline() || !world.equals(mage.getWorld())) {
                    cancel();
                    return;
                }
                double step = Math.min(speed, range - travelled);
                RayTraceResult hit = world.rayTrace(position, direction, step, FluidCollisionMode.NEVER, true, 0.3,
                        entity -> targets.canAffect(mage, entity));
                double length = hit == null ? step : hit.getHitPosition().distance(position.toVector());
                trail(world, position, direction, length);
                if (hit != null) {
                    cancel();
                    world.spawnParticle(Particle.WITCH, hit.getHitPosition().toLocation(world), 12, 0.15, 0.15, 0.15, 0.05);
                    if (hit.getHitEntity() instanceof LivingEntity target) {
                        targets.damage(mage, target, damage);
                        mana.add(mage.getUniqueId(), manaPerHit);
                    }
                    return;
                }
                position.add(direction.clone().multiply(step));
                travelled += step;
                if (travelled >= range) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Partículas roxas ao longo do trecho percorrido neste tick. */
    private static void trail(World world, Location from, Vector direction, double length) {
        Location point = from.clone();
        Vector stride = direction.clone().multiply(0.5);
        for (double d = 0; d < length; d += 0.5) {
            world.spawnParticle(Particle.WITCH, point, 1, 0.03, 0.03, 0.03, 0);
            if (d % 1.0 == 0) {
                world.spawnParticle(Particle.ENCHANT, point, 1, 0.1, 0.1, 0.1, 0.2);
            }
            point.add(stride);
        }
    }
}
