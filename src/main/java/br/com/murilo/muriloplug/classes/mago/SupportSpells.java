package br.com.murilo.muriloplug.classes.mago;

import org.bukkit.Color;
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

/** Staffs de Escudo (absorção no Mago) e de Luz (clarão que cega e revela). Nada aqui toca em blocos. */
final class SupportSpells {

    private static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(255, 200, 40), 1.2f);
    private static final Particle.DustOptions WHITE = new Particle.DustOptions(Color.WHITE, 1.6f);
    /** A aura do escudo é redesenhada a cada 5 ticks. */
    private static final int AURA_PERIOD = 5;
    /** Cada ponto de absorção do efeito: 4 de vida por nível. */
    private static final double ABSORPTION_PER_LEVEL = 4;

    private final JavaPlugin plugin;
    private final SpellTargets targets;
    private final MagoSettings settings;
    private final IllusionSpell illusion;

    SupportSpells(JavaPlugin plugin, SpellTargets targets, MagoSettings settings, IllusionSpell illusion) {
        this.plugin = plugin;
        this.targets = targets;
        this.settings = settings;
        this.illusion = illusion;
    }

    // ------------------------------------------------------------ Escudo

    boolean shield(Player mage) {
        StaffType type = StaffType.ESCUDO;
        double absorption = Math.max(0, settings.staff(type, "absorcao"));
        int ticks = (int) Math.round(Math.max(0, settings.staff(type, "segundos")) * 20);
        int amplifier = Math.max(0, (int) Math.ceil(absorption / ABSORPTION_PER_LEVEL) - 1);

        // O efeito de vanilla faz os corações amarelos sumirem junto com o tempo; o valor exato vem logo depois.
        mage.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, ticks, amplifier));
        mage.setAbsorptionAmount(absorption);

        World world = mage.getWorld();
        world.playSound(mage.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.5f);
        world.spawnParticle(Particle.ENCHANT, mage.getLocation().add(0, 1, 0), 40, 0.6, 0.8, 0.6, 0.5);

        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (!mage.isOnline() || mage.isDead() || elapsed >= ticks) {
                    cancel();
                    return;
                }
                double base = elapsed * 0.35;
                for (int i = 0; i < 6; i++) {
                    double angle = base + i * Math.PI / 3;
                    Location point = mage.getLocation().add(Math.cos(angle) * 0.9, 0.2 + (i % 3) * 0.7, Math.sin(angle) * 0.9);
                    mage.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, GOLD);
                }
                elapsed += AURA_PERIOD;
            }
        }.runTaskTimer(plugin, 0L, AURA_PERIOD);
        return true;
    }

    // ------------------------------------------------------------ Luz

    boolean light(Player mage) {
        StaffType type = StaffType.LUZ;
        double radius = settings.staff(type, "raio");
        int blindTicks = (int) Math.round(settings.staff(type, "cegueira-segundos") * 20);
        int glowTicks = (int) Math.round(settings.staff(type, "brilho-segundos") * 20);

        World world = mage.getWorld();
        Location center = mage.getLocation().add(0, 1, 0);
        for (LivingEntity target : targets.nearby(mage, center, radius)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindTicks, 0));
            if (target instanceof Player player
                    && (player.hasPotionEffect(PotionEffectType.INVISIBILITY) || illusion.isActive(player.getUniqueId()))) {
                player.removePotionEffect(PotionEffectType.INVISIBILITY);
                illusion.reveal(player);
                player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, glowTicks, 0));
            }
        }

        world.spawnParticle(Particle.FLASH, center, 1, 0, 0, 0, 0, Color.WHITE);
        world.spawnParticle(Particle.END_ROD, center, 80, radius / 3, radius / 4, radius / 3, 0.15);
        for (int i = 0; i < 32; i++) {
            double angle = Math.PI * 2 * i / 32;
            world.spawnParticle(Particle.DUST, center.clone().add(Math.cos(angle) * radius, -0.8, Math.sin(angle) * radius),
                    1, 0, 0, 0, 0, WHITE);
        }
        world.playSound(center, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1.5f, 1.2f);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.6f);
        return true;
    }
}
