package br.com.murilo.muriloplug.classes.mago;

import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Staffs de Veneno, Meteoro, Sangue, Gravidade e Tempo. Todo dano passa por {@link SpellTargets#damage} (dano mágico
 * do Mago, com Poder Arcano) e todo alvo por {@link SpellTargets#canAffect} (nunca o clan). Nada aqui toca em blocos.
 * Cada magia devolve false, sem gastar nada, quando não achou onde ou em quem agir.
 */
final class ControlSpells {

    private static final Particle.DustOptions GREEN = new Particle.DustOptions(Color.fromRGB(70, 200, 50), 1.6f);
    private static final Particle.DustOptions RED = new Particle.DustOptions(Color.fromRGB(220, 20, 20), 1.4f);
    private static final Particle.DustOptions BLOOD = new Particle.DustOptions(Color.fromRGB(150, 0, 0), 1.0f);
    private static final Particle.DustOptions DARK = new Particle.DustOptions(Color.fromRGB(40, 0, 60), 1.4f);
    private static final Particle.DustOptions CYAN = new Particle.DustOptions(Color.fromRGB(80, 200, 255), 1.0f);

    /** A nuvem de veneno confere e renova os alvos a cada 5 ticks. */
    private static final int CLOUD_PERIOD = 5;
    /** O aviso do meteoro e a área do Tempo redesenham a cada 2 ticks. */
    private static final int VISUAL_PERIOD = 2;
    /** A área do Tempo reaplica os efeitos a cada 6 ticks (3 execuções). */
    private static final int TIME_EFFECT_EVERY = 6;
    private static final int TIME_EFFECT_TICKS = 20;
    private static final double METEOR_HEIGHT = 20;
    /** Amplificador que o cliente lê como negativo (byte): o jogador não consegue pular. */
    private static final int NO_JUMP = 128;
    private static final int SPIRAL_TICKS = 10;

    private final JavaPlugin plugin;
    private final SpellTargets targets;
    private final MagoSettings settings;
    private final BoltSpells bolts;

    ControlSpells(JavaPlugin plugin, SpellTargets targets, MagoSettings settings, BoltSpells bolts) {
        this.plugin = plugin;
        this.targets = targets;
        this.settings = settings;
        this.bolts = bolts;
    }

    // ------------------------------------------------------------ Veneno

    boolean poison(Player mage) {
        StaffType type = StaffType.VENENO;
        double radius = settings.staff(type, "raio");
        int cloudTicks = ticks(settings.staff(type, "nuvem-segundos"));
        int effectTicks = ticks(settings.staff(type, "efeito-segundos"));
        int amplifier = Math.max(0, settings.staffInt(type, "nivel-veneno") - 1);

        bolts.launch(mage, settings.staff(type, "alcance"), Particle.HAPPY_VILLAGER,
                impact -> poisonCloud(mage, impact, radius, cloudTicks, effectTicks, amplifier));
        return true;
    }

    private void poisonCloud(Player mage, Location center, double radius, int cloudTicks, int effectTicks,
                             int amplifier) {
        World world = center.getWorld();
        world.playSound(center, Sound.ENTITY_SPLASH_POTION_BREAK, 1f, 0.7f);
        // O veneno só é renovado quando está acabando: renovar a cada tick reiniciaria o ritmo do dano dele.
        int refreshBelow = Math.max(1, 25 >> amplifier);

        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (!mage.isOnline() || elapsed >= cloudTicks) {
                    cancel();
                    return;
                }
                elapsed += CLOUD_PERIOD;
                world.spawnParticle(Particle.DUST, center, 30, radius / 2, 0.4, radius / 2, 0, GREEN);
                world.spawnParticle(Particle.HAPPY_VILLAGER, center, 6, radius / 2, 0.4, radius / 2, 0);
                for (LivingEntity target : targets.nearby(mage, center, radius)) {
                    PotionEffect current = target.getPotionEffect(PotionEffectType.POISON);
                    if (current == null || current.getAmplifier() < amplifier || current.getDuration() <= refreshBelow) {
                        target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, effectTicks, amplifier));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, CLOUD_PERIOD);
    }

    // ------------------------------------------------------------ Meteoro

    boolean meteor(Player mage) {
        StaffType type = StaffType.METEORO;
        double radius = settings.staff(type, "raio");
        double damage = settings.staff(type, "dano");
        int warnTicks = Math.max(VISUAL_PERIOD, ticks(settings.staff(type, "aviso-segundos")));
        int slowTicks = ticks(settings.staff(type, "lentidao-segundos"));

        Location center = aimedBlock(mage, settings.staff(type, "alcance"));
        if (center == null) {
            return false;
        }
        World world = center.getWorld();
        world.playSound(center, Sound.ENTITY_GHAST_WARN, 1.5f, 0.6f);

        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (elapsed >= warnTicks) {
                    cancel();
                    impact();
                    return;
                }
                // O aviso é forçado: aparece para todos que estiverem na área, não só perto do Mago.
                ring(world, center, radius, Particle.DUST, RED, true);
                Location falling = center.clone().add(0, METEOR_HEIGHT * (1 - elapsed / (double) warnTicks), 0);
                world.spawnParticle(Particle.FLAME, falling, 14, 0.4, 0.4, 0.4, 0.02);
                world.spawnParticle(Particle.LARGE_SMOKE, falling, 6, 0.4, 0.4, 0.4, 0.01);
                elapsed += VISUAL_PERIOD;
            }

            private void impact() {
                for (LivingEntity target : targets.nearby(mage, center, radius)) {
                    targets.damage(mage, target, damage);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowTicks, 0));
                }
                // Só partículas e som: nenhuma explosão de verdade, então nenhum bloco quebra e nada pega fogo.
                world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 2, radius / 3, 0.3, radius / 3, 0);
                world.spawnParticle(Particle.EXPLOSION, center, 25, radius / 2, 0.5, radius / 2, 0);
                world.spawnParticle(Particle.FLAME, center, 80, radius / 2, 0.5, radius / 2, 0.15);
                world.spawnParticle(Particle.LARGE_SMOKE, center, 40, radius / 2, 0.5, radius / 2, 0.05);
                world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.7f);
                world.playSound(center, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.5f, 0.8f);
            }
        }.runTaskTimer(plugin, 0L, VISUAL_PERIOD);
        return true;
    }

    // ------------------------------------------------------------ Sangue

    boolean blood(Player mage) {
        StaffType type = StaffType.SANGUE;
        double damage = settings.staff(type, "dano");
        double healFraction = Math.max(0, settings.staff(type, "cura-porcentagem"));

        World world = mage.getWorld();
        Location eye = mage.getEyeLocation();
        // O rastreio para no primeiro bloco sólido ou alvo: parede na frente = nenhum alvo.
        RayTraceResult hit = world.rayTrace(eye, eye.getDirection(), settings.staff(type, "alcance"),
                FluidCollisionMode.NEVER, true, 0.4, entity -> targets.canAffect(mage, entity));
        if (hit == null || !(hit.getHitEntity() instanceof LivingEntity target)) {
            return false;
        }

        // Cura pelo dano que realmente entrou (Proteção, imunidade e vida restante contam).
        double before = target.getHealth();
        targets.damage(mage, target, damage);
        double dealt = Math.max(0, before - Math.max(0, target.getHealth()));
        if (dealt > 0 && healFraction > 0) {
            AttributeInstance max = mage.getAttribute(Attribute.MAX_HEALTH);
            double limit = max == null ? 20 : max.getValue();
            mage.setHealth(Math.min(limit, mage.getHealth() + dealt * healFraction));
        }

        Location center = SpellTargets.bodyCenter(target);
        SpellTargets.line(world, eye.clone().subtract(0, 0.3, 0), center, Particle.DUST, BLOOD);
        world.spawnParticle(Particle.DUST, center, 20, 0.3, 0.5, 0.3, 0, RED);
        world.spawnParticle(Particle.HEART, mage.getLocation().add(0, 1.8, 0), 4, 0.4, 0.3, 0.4, 0);
        world.playSound(center, Sound.ENTITY_WITCH_DRINK, 1f, 0.6f);
        return true;
    }

    // ------------------------------------------------------------ Gravidade

    boolean gravity(Player mage) {
        StaffType type = StaffType.GRAVIDADE;
        double radius = settings.staff(type, "raio");
        double damage = settings.staff(type, "dano");
        double force = Math.max(0.1, settings.staff(type, "forca"));

        World world = mage.getWorld();
        Location center = SpellTargets.bodyCenter(mage);
        // Sem linha de visão o alvo seria arrastado contra a parede: esses não são afetados.
        List<LivingEntity> pulled = new ArrayList<>();
        for (LivingEntity target : targets.nearby(mage, center, radius)) {
            if (SpellTargets.clearBetween(center, SpellTargets.bodyCenter(target))) {
                pulled.add(target);
            }
        }
        if (pulled.isEmpty()) {
            return false;
        }

        for (LivingEntity target : pulled) {
            Location from = SpellTargets.bodyCenter(target);
            Vector toMage = center.toVector().subtract(from.toVector());
            double distance = toMage.length();
            SpellTargets.line(world, from, center, Particle.DUST, DARK);
            // Depois do dano, que também aplica um empurrão próprio.
            targets.damage(mage, target, damage);
            if (distance < 1.2) {
                continue; // já está colado no Mago
            }
            double speed = Math.min(force, 0.15 + distance * 0.1);
            Vector pull = toMage.normalize().multiply(speed);
            pull.setY(Math.max(-0.2, Math.min(0.4, pull.getY())) + 0.2);
            target.setVelocity(pull);
        }

        world.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1f, 0.6f);
        spiral(mage, radius);
        return true;
    }

    /** Espiral escura que fecha em volta do Mago. */
    private void spiral(Player mage, double radius) {
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!mage.isOnline() || tick >= SPIRAL_TICKS) {
                    cancel();
                    return;
                }
                double progress = tick / (double) SPIRAL_TICKS;
                double reach = radius * (1 - progress);
                Location base = mage.getLocation();
                for (int arm = 0; arm < 4; arm++) {
                    double angle = progress * Math.PI * 4 + arm * Math.PI / 2;
                    Location point = base.clone().add(Math.cos(angle) * reach, 0.2 + progress * 1.4, Math.sin(angle) * reach);
                    mage.getWorld().spawnParticle(Particle.DUST, point, 4, 0.15, 0.15, 0.15, 0, DARK);
                    mage.getWorld().spawnParticle(Particle.PORTAL, point, 3, 0.1, 0.1, 0.1, 0.3);
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ------------------------------------------------------------ Tempo

    boolean time(Player mage) {
        StaffType type = StaffType.TEMPO;
        double radius = settings.staff(type, "raio");
        int totalTicks = ticks(settings.staff(type, "duracao-segundos"));
        int slowAmplifier = Math.max(0, settings.staffInt(type, "nivel-lentidao") - 1);

        Location center = aimedBlock(mage, settings.staff(type, "alcance"));
        if (center == null) {
            return false;
        }
        World world = center.getWorld();
        world.playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 0.5f);

        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (!mage.isOnline() || elapsed >= totalTicks) {
                    cancel();
                    return;
                }
                ring(world, center, radius, Particle.ENCHANT, null, false);
                ring(world, center, radius, Particle.DUST, CYAN, false);
                // Ponteiro do relógio girando.
                double angle = elapsed * 0.3;
                Location tip = center.clone().add(Math.cos(angle) * radius, 0.15, Math.sin(angle) * radius);
                SpellTargets.line(world, center.clone().add(0, 0.15, 0), tip, Particle.END_ROD);
                if (elapsed % TIME_EFFECT_EVERY == 0) {
                    for (LivingEntity target : targets.nearby(mage, center, radius)) {
                        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, TIME_EFFECT_TICKS, slowAmplifier));
                        // Só jogadores: num mob o servidor leria o 128 como um pulo enorme.
                        if (target instanceof Player) {
                            target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, TIME_EFFECT_TICKS, NO_JUMP));
                        }
                    }
                }
                elapsed += VISUAL_PERIOD;
            }
        }.runTaskTimer(plugin, 0L, VISUAL_PERIOD);
        return true;
    }

    // ------------------------------------------------------------ comuns

    /** Ponto de impacto do bloco mirado (um pouco acima da face acertada), ou null se a mira não pega bloco. */
    private static Location aimedBlock(Player mage, double range) {
        Location eye = mage.getEyeLocation();
        RayTraceResult hit = mage.getWorld().rayTraceBlocks(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, true);
        if (hit == null) {
            return null;
        }
        Location point = hit.getHitPosition().toLocation(mage.getWorld());
        if (hit.getHitBlockFace() != null) {
            point.add(hit.getHitBlockFace().getDirection().multiply(0.1));
        }
        return point;
    }

    /** Círculo de partículas no chão, em volta do centro. */
    private static void ring(World world, Location center, double radius, Particle particle, Object data, boolean force) {
        int points = Math.max(24, (int) Math.round(radius * 8));
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points;
            Location point = center.clone().add(Math.cos(angle) * radius, 0.15, Math.sin(angle) * radius);
            world.spawnParticle(particle, point, 1, 0, 0, 0, 0, data, force);
        }
    }

    private static int ticks(double seconds) {
        return (int) Math.round(Math.max(0, seconds) * 20);
    }
}
