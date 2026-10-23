package br.com.murilo.muriloplug.classes.arqueiro;

import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.shop.ShopUi;
import br.com.murilo.muriloplug.teste.TargetRelations;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.time.Duration;

/**
 * Dano das flechas do Arqueiro, multiplicando nesta ordem: dano base da flecha -> distância -> headshot -> Marca do
 * Caçador -> Ultra Foco. A flecha extra da rajada entra como parte do dano base (70%). Também dá o Foco por acerto e
 * apaga as flechas da Chuva depois que caem.
 *
 * <p>Headshot: o ponto de impacto vem de um raio da posição da flecha (antes do movimento do tick, que é onde o jogo
 * a deixa quando chama o dano) na direção da velocidade contra a caixa de colisão do alvo, expandida 0,3 como o jogo
 * faz. Se o impacto está a no máximo {@link #HEAD_BELOW_EYES} abaixo da altura dos olhos ou acima, é na cabeça.
 */
public final class ArcherDamageListener implements Listener {

    /** A cabeça de um jogador tem 0,5 de altura, centrada nos olhos: 0,25 abaixo deles ainda é cabeça. */
    private static final double HEAD_BELOW_EYES = 0.25;
    /** Margem que o jogo soma à caixa do alvo ao testar o acerto de projéteis. */
    private static final double HIT_MARGIN = 0.3;
    private static final long RAIN_REMOVE_TICKS = 100L;

    private final JavaPlugin plugin;
    private final ClassManager classes;
    private final ArqueiroSettings settings;
    private final ShotRegistry shots;
    private final HunterMark marks;
    private final FocusManager focus;
    private final TargetRelations relations;
    /** O dano com headshot que está sendo processado (os efeitos saem no MONITOR, com o dano final). */
    private EntityDamageByEntityEvent pendingHeadshot;

    public ArcherDamageListener(JavaPlugin plugin, ClassManager classes, ArqueiroSettings settings, ShotRegistry shots,
                                HunterMark marks, FocusManager focus, TargetRelations relations) {
        this.plugin = plugin;
        this.classes = classes;
        this.settings = settings;
        this.shots = shots;
        this.marks = marks;
        this.focus = focus;
        this.relations = relations;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof AbstractArrow arrow) || !(arrow.getShooter() instanceof Player shooter)
                || !classes.hasClass(shooter.getUniqueId(), PlayerClass.ARQUEIRO)
                || !(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        ShotRegistry.Shot shot = shots.get(arrow);
        if (shot != null && shot.kind() == ShotRegistry.Kind.RAIN && victim.equals(shooter)) {
            event.setCancelled(true); // a Chuva de Flechas não machuca o próprio Arqueiro
            return;
        }
        double multiplier = 1;
        boolean headshot = false;
        if (shot != null && shot.kind() != ShotRegistry.Kind.RAIN) {
            if (shot.kind() == ShotRegistry.Kind.EXTRA) {
                multiplier *= settings.burstExtraDamage();
            }
            if (shot.special() == SpecialArrow.PERFURANTE) {
                // Cada alvo atravessado leva menos dano que o anterior; quem usa armadura leva a mais.
                multiplier *= Math.pow(1 - settings.pierceDrop(), shot.hits());
                shot.addHit();
                if (wearsArmor(victim)) {
                    multiplier *= 1 + settings.pierceArmorBonus();
                }
            }
            Vector impact = impactPoint(arrow, victim);
            double distance = shot.origin().getWorld() == victim.getWorld()
                    ? shot.origin().toVector().distance(impact) : 0;
            multiplier *= 1 + Math.min(settings.distanceMax(), distance * settings.distancePerBlock());
            headshot = impact.getY() >= victim.getEyeLocation().getY() - HEAD_BELOW_EYES;
            if (headshot) {
                multiplier *= 1 + settings.headshotBonus();
                pendingHeadshot = event;
            }
        }
        if (marks.isMarkedBy(shooter.getUniqueId(), victim)) {
            multiplier *= 1 + settings.markBonus();
        }
        if (shot != null && shot.isUltra()) {
            multiplier *= settings.ultraMultiplier();
        }
        if (multiplier != 1) {
            event.setDamage(event.getDamage() * multiplier);
        }
    }

    /** Com o dano final conhecido: avisa o headshot e dá o Foco do acerto. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageDone(EntityDamageByEntityEvent event) {
        boolean headshot = event == pendingHeadshot;
        pendingHeadshot = null;
        if (!(event.getDamager() instanceof AbstractArrow arrow) || !(arrow.getShooter() instanceof Player shooter)
                || !(event.getEntity() instanceof LivingEntity victim) || event.getFinalDamage() <= 0) {
            return;
        }
        ShotRegistry.Shot shot = shots.get(arrow);
        if (shot == null || shot.kind() == ShotRegistry.Kind.RAIN) {
            return;
        }
        if (headshot) {
            shooter.showTitle(Title.title(ShopUi.text("§c§lHEADSHOT!"), Component.empty(),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(700), Duration.ofMillis(200))));
            shooter.playSound(shooter.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 2f);
            shooter.playSound(shooter.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1f, 0.6f);
            victim.getWorld().spawnParticle(Particle.CRIT, victim.getEyeLocation(), 15, 0.2, 0.2, 0.2, 0.3);
        }
        if (relations.isEnemy(shooter, victim)) {
            focus.add(shooter.getUniqueId(), settings.focusPerHit());
        }
    }

    /** Flecha da Chuva: some depois de cair (um tick depois do impacto, para o dano ser aplicado antes). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow)) {
            return;
        }
        ShotRegistry.Shot shot = shots.get(arrow);
        if (shot != null && shot.kind() == ShotRegistry.Kind.RAIN) {
            plugin.getServer().getScheduler().runTask(plugin, arrow::remove);
        }
    }

    /** Sobra de segurança: apaga a flecha da Chuva mesmo que nada a tenha acertado. */
    public void removeLater(AbstractArrow arrow) {
        plugin.getServer().getScheduler().runTaskLater(plugin, arrow::remove, RAIN_REMOVE_TICKS);
    }

    private static boolean wearsArmor(LivingEntity victim) {
        EntityEquipment equipment = victim.getEquipment();
        if (equipment == null) {
            return false;
        }
        for (ItemStack piece : equipment.getArmorContents()) {
            if (piece != null && !piece.getType().isAir()) {
                return true;
            }
        }
        return false;
    }

    /** Onde a flecha acertou a caixa de colisão do alvo (a própria posição dela, se já estava dentro). */
    private static Vector impactPoint(AbstractArrow arrow, LivingEntity victim) {
        Vector start = arrow.getLocation().toVector();
        Vector velocity = arrow.getVelocity();
        if (velocity.lengthSquared() < 1.0E-6) {
            return start;
        }
        BoundingBox box = victim.getBoundingBox().expand(HIT_MARGIN);
        RayTraceResult result = box.rayTrace(start, velocity.clone().normalize(), Math.max(4, velocity.length() * 2));
        return result == null ? start : result.getHitPosition();
    }
}
