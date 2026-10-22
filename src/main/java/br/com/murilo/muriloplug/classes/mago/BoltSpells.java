package br.com.murilo.muriloplug.classes.mago;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.function.Consumer;

/**
 * Staffs de Fogo e de Gelo: um projétil feito só de partículas que anda na direção da mira e explode em área.
 * Nada aqui toca em blocos: não há explosão de verdade, nem fogo no chão.
 */
final class BoltSpells {

    /** Blocos andados por tick. */
    private static final double STEP = 1.2;

    private final JavaPlugin plugin;
    private final SpellTargets targets;
    private final MagoSettings settings;
    private final MagoCombos combos;

    BoltSpells(JavaPlugin plugin, SpellTargets targets, MagoSettings settings, MagoCombos combos) {
        this.plugin = plugin;
        this.targets = targets;
        this.settings = settings;
        this.combos = combos;
    }

    boolean fire(Player mage) {
        StaffType type = StaffType.FOGO;
        double baseRadius = settings.staff(type, "raio");
        double damage = settings.staff(type, "dano");
        int burnTicks = (int) (settings.staff(type, "fogo-segundos") * 20);
        int blindTicks = (int) (settings.staff(type, "cegueira-segundos") * 20);

        launch(mage, settings.staff(type, "alcance"), Particle.FLAME, impact -> {
            List<LivingEntity> hit = targets.nearby(mage, impact, baseRadius);
            double radius = baseRadius;
            // Tempestade de Fogo (Vento -> Fogo): alvo empurrado pelo Vento aumenta a explosão.
            if (combos.stormPending(hit)) {
                radius = Math.max(baseRadius, combos.stormRadius());
                hit = targets.nearby(mage, impact, radius);
            }
            for (LivingEntity target : hit) {
                // Choque Térmico (Gelo -> Fogo): o dano dobra e o efeito de gelo sai.
                targets.damage(mage, target, combos.thermalShock(mage, target, damage));
                target.setFireTicks(combos.stormHit(mage, target) ? Math.max(burnTicks, combos.stormBurnTicks()) : burnTicks);
                target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindTicks, 0));
            }
            World world = impact.getWorld();
            world.spawnParticle(Particle.FLAME, impact, 60, radius / 2, radius / 2, radius / 2, 0.05);
            world.spawnParticle(Particle.EXPLOSION, impact, 3, radius / 3, radius / 3, radius / 3, 0);
            world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
        });
        return true;
    }

    boolean ice(Player mage) {
        StaffType type = StaffType.GELO;
        double radius = settings.staff(type, "raio");
        double damage = settings.staff(type, "dano");
        int effectTicks = (int) (settings.staff(type, "efeito-segundos") * 20);

        launch(mage, settings.staff(type, "alcance"), Particle.SNOWFLAKE, impact -> {
            for (LivingEntity target : targets.nearby(mage, impact, radius)) {
                targets.damage(mage, target, damage);
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, effectTicks, 1));
                target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, effectTicks, 0));
                combos.markIce(target);
            }
            World world = impact.getWorld();
            world.spawnParticle(Particle.SNOWFLAKE, impact, 80, radius / 2, radius / 2, radius / 2, 0.05);
            world.playSound(impact, Sound.BLOCK_GLASS_BREAK, 1f, 1f);
        });
        return true;
    }

    /** Anda 1 passo por tick na direção da mira; ao acertar um alvo ou bloco (ou no alcance máximo) chama {@code onImpact}. */
    void launch(Player mage, double range, Particle trail, Consumer<Location> onImpact) {
        World world = mage.getWorld();
        Location position = mage.getEyeLocation();
        Vector direction = position.getDirection().normalize();
        world.playSound(position, Sound.ENTITY_BLAZE_SHOOT, 0.8f, 1.2f);

        new BukkitRunnable() {
            double travelled = 0;

            @Override
            public void run() {
                if (!mage.isOnline() || !position.getWorld().equals(mage.getWorld())) {
                    cancel();
                    return;
                }
                RayTraceResult hit = world.rayTrace(position, direction, STEP, FluidCollisionMode.NEVER, true, 0.3,
                        entity -> targets.canAffect(mage, entity));
                if (hit != null) {
                    cancel();
                    Location impact = hit.getHitPosition().toLocation(world).subtract(direction.clone().multiply(0.1));
                    onImpact.accept(impact);
                    return;
                }
                position.add(direction.clone().multiply(STEP));
                travelled += STEP;
                world.spawnParticle(trail, position, 4, 0.1, 0.1, 0.1, 0.01);
                if (travelled >= range) {
                    cancel();
                    onImpact.accept(position.clone());
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
