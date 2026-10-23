package br.com.murilo.muriloplug.classes.arqueiro;

import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.GrapplePull;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.classes.suporte.RootManager;
import br.com.murilo.muriloplug.teste.StatusRegistry;
import br.com.murilo.muriloplug.teste.TargetRelations;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Flechas especiais do Arqueiro (Perfurante, Gelo, Fumaça, Corrente e Corda; a Explosiva fica em
 * {@link ExplosiveArrowListener}). Só o Arqueiro atira, e só com ARCO: qualquer outro caso cancela o tiro sem gastar
 * a flecha. Elas são marcadas no PersistentDataContainer da entidade, sempre gastam (a Recuperação ignora flechas
 * especiais) e não podem ser recolhidas. O dano segue as regras de {@link ArcherDamageListener}.
 *
 * <p>Perfurante: atravessa alvos com o {@code pierceLevel} do próprio jogo (o dano de cada um é ajustado no
 * {@link ArcherDamageListener}) e, ao bater em uma parede fina, a flecha é trocada por uma flecha COMUM do outro lado.
 */
public final class SpecialArrowListener implements Listener {

    private static final double FULL_SPEED = 3.0;
    private static final double WALL_STEP = 0.05;

    private final JavaPlugin plugin;
    private final ClassManager classes;
    private final ArqueiroSettings settings;
    private final ArrowItems arrows;
    private final ShotRegistry shots;
    private final UltraFocus ultra;
    private final FocusManager focus;
    private final ArcherTargets targets;
    private final TargetRelations relations;
    private final RootManager roots;
    private final StatusRegistry statuses;
    private final GrapplePull pull;
    /** Momento (ms) do último tiro de Flecha de Corda de cada Arqueiro. */
    private final Map<UUID, Long> lastRope = new HashMap<>();
    /** Flechas de Fumaça que já criaram a nuvem. */
    private final Set<UUID> smoked = new HashSet<>();

    public SpecialArrowListener(JavaPlugin plugin, ClassManager classes, ArqueiroSettings settings, ArrowItems arrows,
                                ShotRegistry shots, UltraFocus ultra, FocusManager focus, ArcherTargets targets,
                                TargetRelations relations, RootManager roots, StatusRegistry statuses,
                                GrapplePull pull) {
        this.plugin = plugin;
        this.classes = classes;
        this.settings = settings;
        this.arrows = arrows;
        this.shots = shots;
        this.ultra = ultra;
        this.focus = focus;
        this.targets = targets;
        this.relations = relations;
        this.roots = roots;
        this.statuses = statuses;
        this.pull = pull;
    }

    private boolean isArcher(Player player) {
        return classes.hasClass(player.getUniqueId(), PlayerClass.ARQUEIRO);
    }

    private int ropeWaitSeconds(UUID id) {
        Long last = lastRope.get(id);
        if (last == null) {
            return 0;
        }
        long left = last + (long) (settings.ropeCooldownSeconds() * 1000) - System.currentTimeMillis();
        return left <= 0 ? 0 : (int) Math.ceil(left / 1000.0);
    }

    // ------------------------------------------------------------ o tiro

    /** Roda antes do {@link ArcherBowListener} (HIGHEST), que registra o tiro já com o tipo da flecha. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack ammo = event.getConsumable();
        SpecialArrow type = arrows.typeOf(ammo);
        if (type == null || type == SpecialArrow.EXPLOSIVA) {
            return;
        }
        ItemStack bow = event.getBow();
        if (!isArcher(player) || bow == null || bow.getType() != Material.BOW) {
            deny(event, player, "§cEsta flecha só funciona com ARCO e só para Arqueiros.");
            return;
        }
        if (type == SpecialArrow.CORDA) {
            int wait = ropeWaitSeconds(player.getUniqueId());
            if (wait > 0) {
                deny(event, player, "§cCorda em recarga: " + wait + "s");
                return;
            }
        }
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) {
            return;
        }
        if (type == SpecialArrow.CORDA) {
            lastRope.put(player.getUniqueId(), System.currentTimeMillis());
        }
        arrows.markProjectile(arrow.getPersistentDataContainer(), type);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        // Com Infinidade o jogo não gasta a flecha; a especial sempre gasta (no criativo nada é gasto).
        if (bow.containsEnchantment(Enchantment.INFINITY) && player.getGameMode() != GameMode.CREATIVE) {
            ArcherBowListener.takeOne(player.getInventory(), ammo);
        }
        if (type == SpecialArrow.PERFURANTE) {
            heavyShot(event, player, arrow);
        }
    }

    private void deny(EntityShootBowEvent event, Player player, String message) {
        event.setCancelled(true);
        player.updateInventory();
        focus.warn(player, message);
        player.sendMessage(message);
    }

    /** Perfurante: força proporcional ao tempo puxado (total em {@code ticks-forca-total}) e atravessa alvos. */
    private void heavyShot(EntityShootBowEvent event, Player player, AbstractArrow arrow) {
        int pulled = ultra.pulledTicks(player);
        double fraction = pulled > 0 ? Math.min(1.0, pulled / (double) settings.pierceFullTicks())
                : Math.min(1.0, event.getForce());
        arrow.setVelocity(arrow.getVelocity().normalize().multiply(FULL_SPEED * fraction));
        arrow.setCritical(fraction >= 1.0);
        arrow.setPierceLevel(settings.pierceMaxTargets() - 1);
    }

    // ------------------------------------------------------------ impacto

    @EventHandler(priority = EventPriority.HIGH)
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow)) {
            return;
        }
        SpecialArrow type = arrows.typeOfProjectile(arrow.getPersistentDataContainer());
        if (type == null) {
            return;
        }
        Player shooter = arrow.getShooter() instanceof Player player ? player : null;
        switch (type) {
            case PERFURANTE -> {
                if (event.getHitBlock() != null) {
                    passWall(event, arrow);
                }
            }
            case FUMACA -> {
                if (smoked.add(arrow.getUniqueId())) {
                    smoke(shooter, cloudCenter(event, arrow));
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        smoked.remove(arrow.getUniqueId());
                        arrow.remove();
                    });
                }
            }
            case CORDA -> {
                if (event.getHitBlock() != null) {
                    rope(shooter, arrow, event.getHitBlockFace());
                }
                plugin.getServer().getScheduler().runTask(plugin, arrow::remove);
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------ Gelo e Corrente

    /** Gelo e Corrente agem no acerto (com dano de verdade: aliado, escudo e dano cancelado não valem). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof AbstractArrow arrow) || !(arrow.getShooter() instanceof Player shooter)
                || !(event.getEntity() instanceof LivingEntity victim) || event.getFinalDamage() <= 0
                || relations.isAlly(shooter, victim)) {
            return;
        }
        SpecialArrow type = arrows.typeOfProjectile(arrow.getPersistentDataContainer());
        if (type == SpecialArrow.GELO) {
            double seconds = settings.iceSeconds();
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) (seconds * 20),
                    settings.iceLevel() - 1));
            statuses.add(victim, "Congelado", seconds);
            victim.getWorld().spawnParticle(Particle.SNOWFLAKE, victim.getLocation().add(0, victim.getHeight() / 2, 0),
                    25, 0.3, 0.5, 0.3, 0.02);
            victim.getWorld().playSound(victim.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8f, 1.5f);
        } else if (type == SpecialArrow.CORRENTE) {
            roots.hold(victim, settings.chainSeconds());
        }
    }

    // ------------------------------------------------------------ Fumaça

    private Location cloudCenter(ProjectileHitEvent event, AbstractArrow arrow) {
        if (event.getHitEntity() != null) {
            return event.getHitEntity().getBoundingBox().getCenter().toLocation(arrow.getWorld());
        }
        Block block = event.getHitBlock();
        BlockFace face = event.getHitBlockFace();
        if (block == null || face == null) {
            return arrow.getLocation();
        }
        Location center = block.getRelative(face).getLocation().add(0.5, 0.5, 0.5);
        return face == BlockFace.UP ? center.add(0, 0.5, 0) : center;
    }

    /** Nuvem que dura {@code segundos}: partículas densas e Cegueira (renovada a cada segundo) nos inimigos dentro. */
    private void smoke(Player shooter, Location center) {
        double radius = settings.smokeRadius();
        int duration = settings.smokeSeconds() * 20;
        World world = center.getWorld();
        world.playSound(center, Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.6f);
        new BukkitRunnable() {
            int elapsed;

            @Override
            public void run() {
                if (elapsed >= duration) {
                    cancel();
                    return;
                }
                if (elapsed % 2 == 0) {
                    world.spawnParticle(Particle.LARGE_SMOKE, center, 35, radius * 0.45, radius * 0.3, radius * 0.45, 0.01);
                    world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, center, 6, radius * 0.4, radius * 0.25, radius * 0.4, 0.01);
                }
                if (elapsed % 20 == 0 && shooter != null && shooter.isOnline()) {
                    blind();
                }
                elapsed++;
            }

            private void blind() {
                for (LivingEntity entity : world.getNearbyLivingEntities(center, radius)) {
                    if (entity.getBoundingBox().getCenter().distanceSquared(center.toVector()) <= radius * radius
                            && targets.isEnemy(shooter, entity)) {
                        entity.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, true));
                        statuses.add(entity, "Cego", 2);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ------------------------------------------------------------ Corda

    private void rope(Player shooter, AbstractArrow arrow, BlockFace face) {
        if (shooter == null || !shooter.isOnline() || shooter.isDead() || !isArcher(shooter)
                || !shooter.getWorld().equals(arrow.getWorld())) {
            return;
        }
        Location impact = arrow.getLocation();
        if (shooter.getLocation().distance(impact) > settings.ropeRange()) {
            focus.warn(shooter, "§cAlvo longe demais (máximo " + (int) settings.ropeRange() + " blocos).");
            return;
        }
        pull.pull(shooter, GrapplePull.target(impact, face), settings.ropeSpeed(), settings.ropeFallProtectionSeconds());
    }

    // ------------------------------------------------------------ Perfurante atravessa parede

    /**
     * A flecha bateu em um bloco. Se a parede tem no máximo {@code espessura-max-parede} blocos, a flecha é removida e,
     * no tick seguinte, uma flecha COMUM (sem a marca de Perfurante: sem atravessar, sem bônus) nasce do outro lado
     * com a mesma velocidade. Parede grossa: a flecha fica presa nela.
     */
    private void passWall(ProjectileHitEvent event, AbstractArrow arrow) {
        Vector velocity = arrow.getVelocity();
        int thickness = settings.pierceWallThickness();
        if (thickness < 1 || velocity.lengthSquared() < 1.0E-6) {
            return;
        }
        World world = arrow.getWorld();
        Vector direction = velocity.clone().normalize();
        Vector exit = findExit(world, entryPoint(arrow, event.getHitBlock(), direction, velocity.length()), direction, thickness);
        if (exit == null) {
            return;
        }
        event.setCancelled(true);
        Location spawn = exit.add(direction.clone().multiply(0.3)).toLocation(world);
        ShotRegistry.Shot old = shots.get(arrow);
        ShotRegistry.Kind kind = old == null ? null : old.kind();
        Location origin = old == null ? null : old.origin();
        boolean ultraShot = old != null && old.isUltra();
        double damage = arrow.getDamage();
        boolean critical = arrow.isCritical();
        int fire = arrow.getFireTicks();
        ItemStack weapon = arrow.getWeapon() == null ? null : arrow.getWeapon().clone();
        Object shooter = arrow.getShooter();
        arrow.remove();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Arrow replacement = world.spawn(spawn, Arrow.class, spawned -> {
                if (shooter instanceof Player player) {
                    spawned.setShooter(player);
                }
                spawned.setDamage(damage);
                spawned.setCritical(critical);
                spawned.setFireTicks(fire);
                spawned.setPierceLevel(0);
                spawned.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                if (weapon != null) {
                    spawned.setWeapon(weapon);
                }
                spawned.setVelocity(velocity);
                if (kind != null) {
                    ShotRegistry.Shot shot = shots.register(spawned, kind, origin);
                    shot.setUltra(ultraShot);
                }
            });
            if (ultraShot) {
                ultra.trail(replacement);
            }
        });
        world.spawnParticle(Particle.CRIT, spawn, 10, 0.1, 0.1, 0.1, 0.2);
    }

    /** Onde a flecha entrou no bloco (pelo raio da posição dela; o centro do bloco se o raio falhar). */
    private static Vector entryPoint(AbstractArrow arrow, Block block, Vector direction, double speed) {
        RayTraceResult result = arrow.getWorld().rayTraceBlocks(arrow.getLocation(), direction, speed + 1,
                FluidCollisionMode.NEVER, true);
        if (result != null && block.equals(result.getHitBlock())) {
            return result.getHitPosition();
        }
        return block.getLocation().add(0.5, 0.5, 0.5).toVector();
    }

    /**
     * Anda pela parede em passos pequenos contando os blocos sólidos diferentes. Se não passar de {@code thickness}
     * e logo depois houver espaço livre, devolve o ponto de saída; senão null.
     */
    private static Vector findExit(World world, Vector entry, Vector direction, int thickness) {
        Set<Long> solid = new HashSet<>();
        double max = thickness * 1.8 + 0.6;
        for (double distance = 0.02; distance <= max; distance += WALL_STEP) {
            Vector point = entry.clone().add(direction.clone().multiply(distance));
            Block block = world.getBlockAt(point.getBlockX(), point.getBlockY(), point.getBlockZ());
            if (!block.isPassable()) {
                solid.add(block.getBlockKey());
                if (solid.size() > thickness) {
                    return null;
                }
            } else if (!solid.isEmpty()) {
                return point;
            }
        }
        return null;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastRope.remove(event.getPlayer().getUniqueId());
    }
}
