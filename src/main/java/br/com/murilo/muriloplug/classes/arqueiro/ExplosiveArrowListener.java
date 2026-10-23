package br.com.murilo.muriloplug.classes.arqueiro;

import br.com.murilo.muriloplug.teste.TargetRelations;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Efeitos da Flecha Explosiva: dano multiplicado no acerto direto em jogador ou boneco e uma "explosão" própria no
 * impacto. A explosão é só efeito visual e sonoro (partículas e som, sem {@code createExplosion}); o dano é calculado
 * aqui para todas as entidades vivas no raio, do máximo no centro até 0 na borda, como dano de EXPLOSÃO causado pelo
 * Arqueiro (armadura, Proteção e a passiva do Engenheiro reduzem; conta no combate e no KDA). Nada quebra, pega fogo
 * ou é destruído, e o Arqueiro e o clan dele não são atingidos. Sai 1 tick depois do impacto, para o dano da flecha
 * ser aplicado antes dela. Os bônus de distância, headshot e Marca só multiplicam o dano da flecha (acerto direto),
 * nunca o da explosão, pois dependem da flecha como causadora.
 */
public final class ExplosiveArrowListener implements Listener {

    private final JavaPlugin plugin;
    private final ArqueiroSettings settings;
    private final ArrowItems arrows;
    private final TargetRelations relations;
    /** Flechas que já têm a explosão agendada. */
    private final Set<UUID> pending = new HashSet<>();

    public ExplosiveArrowListener(JavaPlugin plugin, ArqueiroSettings settings, ArrowItems arrows,
                                  TargetRelations relations) {
        this.relations = relations;
        this.plugin = plugin;
        this.settings = settings;
        this.arrows = arrows;
    }

    /** Dano da flecha multiplicado só no acerto direto em jogador ou boneco. Roda depois do bloqueio de fogo amigo (LOW). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArrowDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof AbstractArrow arrow
                && (event.getEntity() instanceof Player || relations.isDummy(event.getEntity()))
                && arrows.isExplosiveProjectile(arrow.getPersistentDataContainer())) {
            event.setDamage(event.getDamage() * settings.explosiveDamageMultiplier());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow)
                || !arrows.isExplosiveProjectile(arrow.getPersistentDataContainer())
                || !pending.add(arrow.getUniqueId())) {
            return;
        }
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        Location impact = arrow.getLocation().clone();
        LivingEntity direct = event.getHitEntity() instanceof LivingEntity living ? living : null;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            pending.remove(arrow.getUniqueId());
            arrow.remove();
            if (arrow.getShooter() instanceof Player shooter && shooter.isOnline() && impact.getWorld() != null) {
                explode(shooter, impact, direct);
            }
        });
    }

    private void explode(Player shooter, Location center, LivingEntity direct) {
        World world = center.getWorld();
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.1f);

        double radius = settings.explosionRadius();
        double maxDamage = settings.explosionDamage();
        DamageSource source = DamageSource.builder(DamageType.EXPLOSION)
                .withCausingEntity(shooter).withDirectEntity(shooter).withDamageLocation(center).build();
        for (LivingEntity target : world.getNearbyLivingEntities(center, radius)) {
            if (target.equals(shooter) || target instanceof ArmorStand || target.isDead()
                    || relations.isAlly(shooter, target)
                    || (target instanceof Player player && player.getGameMode() == GameMode.SPECTATOR)) {
                continue;
            }
            Vector offset = target.getBoundingBox().getCenter().subtract(center.toVector());
            double distance = offset.length();
            if (distance >= radius) {
                continue;
            }
            double falloff = 1 - distance / radius;
            if (target.equals(direct)) {
                target.setNoDamageTicks(0); // senão a imunidade do acerto da flecha engoliria a explosão
            }
            if (maxDamage * falloff > 0) {
                target.damage(maxDamage * falloff, source);
            }
            knockBack(target, offset, falloff);
        }
    }

    /** Empurrão leve para longe do centro, mais fraco perto da borda. */
    private void knockBack(LivingEntity target, Vector offset, double falloff) {
        double strength = settings.explosionKnockback() * falloff;
        if (strength <= 0 || target.isDead()) {
            return;
        }
        Vector push = offset.clone().setY(0);
        push = push.lengthSquared() < 1.0E-6 ? new Vector() : push.normalize().multiply(strength);
        push.setY(0.2 * falloff);
        target.setVelocity(target.getVelocity().add(push));
    }
}
