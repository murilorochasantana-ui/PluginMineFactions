package br.com.murilo.muriloplug.classes.arqueiro;

import br.com.murilo.muriloplug.auth.AuthManager;
import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Habilidades do Arqueiro que dependem de clique: Marca do Caçador (clique esquerdo mirando em um inimigo), Chuva de
 * Flechas (Shift + clique esquerdo mirando em um bloco), a passiva Olhos de Águia (agachado com o arco, os inimigos
 * aparecem só para ele) e a dica das teclas ao pegar o arco.
 *
 * <p>O clique esquerdo em uma entidade chega como golpe, não como PlayerInteractEvent; por isso o golpe corpo a corpo
 * com o arco na mão também aciona a habilidade (o golpe com um arco não serve para nada mesmo).
 */
public final class ArcherSkills implements Listener {

    private static final String TIP = "§7[F] Rajada §8| §7[Shift+F] Dash §8| §7[Clique Esq] Marca §8| "
            + "§7[Shift+Clique Esq] Chuva §8| §7[Q] Ultra Foco";
    private static final long CLICK_DEBOUNCE_MILLIS = 250L;
    private static final long EAGLE_PERIOD = 5L;
    private static final double RAIN_FALL_SPEED = 3.0;
    private static final double RAIN_ARROW_DAMAGE = 2.0;
    private static final double RAIN_MAX_HEIGHT = 20.0;
    private static final int RAIN_SPREAD_TICKS = 10;
    private static final int CIRCLE_POINTS = 32;
    private static final Particle.DustOptions CIRCLE_DUST = new Particle.DustOptions(Color.fromRGB(255, 70, 70), 1.4f);
    private static final Particle.DustOptions EAGLE_DUST = new Particle.DustOptions(Color.fromRGB(255, 40, 40), 1.6f);

    private final JavaPlugin plugin;
    private final ClassManager classes;
    private final ArqueiroSettings settings;
    private final FocusManager focus;
    private final HunterMark marks;
    private final ShotRegistry shots;
    private final ArcherDamageListener damage;
    private final ArcherTargets targets;
    private final AuthManager auth;
    private final Map<UUID, Long> lastClick = new HashMap<>();

    public ArcherSkills(JavaPlugin plugin, ClassManager classes, ArqueiroSettings settings, FocusManager focus,
                        HunterMark marks, ShotRegistry shots, ArcherDamageListener damage, ArcherTargets targets,
                        AuthManager auth) {
        this.plugin = plugin;
        this.classes = classes;
        this.settings = settings;
        this.focus = focus;
        this.marks = marks;
        this.shots = shots;
        this.damage = damage;
        this.targets = targets;
        this.auth = auth;
    }

    private boolean isArcher(Player player) {
        return classes.hasClass(player.getUniqueId(), PlayerClass.ARQUEIRO);
    }

    private boolean holdsBow(Player player) {
        return settings.isArcherBow(player.getInventory().getItemInMainHand());
    }

    /** Liga os Olhos de Águia. */
    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::eagleEyes, EAGLE_PERIOD, EAGLE_PERIOD);
    }

    // ------------------------------------------------------------ cliques

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        Player player = event.getPlayer();
        if ((action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) || event.getHand() != EquipmentSlot.HAND
                || !isArcher(player) || !holdsBow(player)) {
            return;
        }
        boolean used = leftClick(player);
        // Shift + clique esquerdo nunca quebra o bloco mirado; a Marca só impede a quebra se marcou alguém.
        if (used || (player.isSneaking() && action == Action.LEFT_CLICK_BLOCK)) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setCancelled(true);
        }
    }

    /** Golpe corpo a corpo com o arco na mão (inclusive Shift): vira o clique esquerdo da habilidade. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPunch(EntityDamageByEntityEvent event) {
        if (event.getCause() != DamageCause.ENTITY_ATTACK || !(event.getDamager() instanceof Player player)
                || !isArcher(player) || !holdsBow(player)) {
            return;
        }
        event.setCancelled(true);
        leftClick(player);
    }

    /** Retorna true se usou uma habilidade (gastou Foco). */
    private boolean leftClick(Player player) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastClick.get(id);
        if (!auth.isLoggedIn(id) || (last != null && now - last < CLICK_DEBOUNCE_MILLIS)) {
            return false;
        }
        lastClick.put(id, now);
        return player.isSneaking() ? rain(player) : mark(player);
    }

    // ------------------------------------------------------------ Marca do Caçador

    private boolean mark(Player player) {
        double range = settings.markRange();
        Location eye = player.getEyeLocation();
        RayTraceResult hit = player.getWorld().rayTrace(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, true,
                0.3, entity -> targets.isEnemy(player, entity));
        if (hit == null || !(hit.getHitEntity() instanceof LivingEntity target)) {
            return false; // sem alvo (ou parede na frente): não faz nada e não gasta
        }
        UUID id = player.getUniqueId();
        int cost = settings.markCost();
        if (!focus.spend(id, cost)) {
            focus.warnInsufficient(player, cost);
            return false;
        }
        marks.mark(player, target, settings.markSeconds());
        Location at = target.getEyeLocation();
        at.getWorld().spawnParticle(Particle.CRIT, at, 20, 0.3, 0.3, 0.3, 0.2);
        player.playSound(player.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1f, 0.5f);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.5f);
        focus.warn(player, "§cMarca do Caçador! §7(" + settings.markSeconds() + "s)");
        return true;
    }

    // ------------------------------------------------------------ Chuva de Flechas

    private boolean rain(Player player) {
        double range = settings.rainRange();
        Location eye = player.getEyeLocation();
        RayTraceResult hit = player.getWorld().rayTraceBlocks(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, true);
        if (hit == null || hit.getHitBlock() == null) {
            focus.warn(player, "§cMire em um bloco (até " + (int) range + " blocos)");
            return false;
        }
        UUID id = player.getUniqueId();
        int cost = settings.rainCost();
        if (!focus.spend(id, cost)) {
            focus.warnInsufficient(player, cost);
            return false;
        }
        Location center = hit.getHitPosition().toLocation(player.getWorld());
        if (hit.getHitBlockFace() != null) {
            center.add(hit.getHitBlockFace().getDirection().multiply(0.05));
        }
        double radius = settings.rainRadius();
        int delay = settings.rainDelayTicks();
        player.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 0.6f);
        focus.warn(player, "§9Chuva de Flechas!");
        showCircle(center, radius, delay);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                dropArrows(player, center, radius);
            }
        }, delay);
        return true;
    }

    /** Círculo de partículas no chão durante o atraso (de 4 em 4 ticks). */
    private void showCircle(Location center, double radius, int duration) {
        World world = center.getWorld();
        for (int tick = 0; tick <= duration; tick += 4) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                for (int i = 0; i < CIRCLE_POINTS; i++) {
                    double angle = 2 * Math.PI * i / CIRCLE_POINTS;
                    world.spawnParticle(Particle.DUST,
                            center.getX() + Math.cos(angle) * radius, center.getY() + 0.1, center.getZ() + Math.sin(angle) * radius,
                            1, 0, 0, 0, 0, CIRCLE_DUST);
                }
                world.spawnParticle(Particle.DUST, center.getX(), center.getY() + 0.1, center.getZ(), 3, 0.2, 0.05, 0.2, 0, CIRCLE_DUST);
            }, tick);
        }
    }

    /** As flechas caem do céu espalhadas na área, com o dano de uma flecha normal totalmente puxada. */
    private void dropArrows(Player player, Location center, double radius) {
        World world = center.getWorld();
        RayTraceResult ceiling = world.rayTraceBlocks(center.clone().add(0, 0.5, 0), new Vector(0, 1, 0),
                RAIN_MAX_HEIGHT, FluidCollisionMode.NEVER, true);
        double height = ceiling == null ? RAIN_MAX_HEIGHT : Math.max(2, ceiling.getHitPosition().getY() - center.getY() - 1);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < settings.rainArrows(); i++) {
            double distance = radius * Math.sqrt(random.nextDouble());
            double angle = random.nextDouble(2 * Math.PI);
            Location from = center.clone().add(Math.cos(angle) * distance, height, Math.sin(angle) * distance);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    launchRainArrow(player, from);
                }
            }, random.nextInt(RAIN_SPREAD_TICKS + 1));
        }
    }

    private void launchRainArrow(Player player, Location from) {
        World world = from.getWorld();
        AbstractArrow arrow = world.spawn(from, Arrow.class, spawned -> {
            spawned.setShooter(player);
            spawned.setDamage(RAIN_ARROW_DAMAGE);
            spawned.setCritical(false);
            spawned.setPierceLevel(0);
            spawned.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            spawned.setVelocity(new Vector(0, -RAIN_FALL_SPEED, 0));
            shots.register(spawned, ShotRegistry.Kind.RAIN, from);
        });
        damage.removeLater(arrow);
        world.playSound(from, Sound.ENTITY_ARROW_SHOOT, 0.3f, 0.8f);
    }

    // ------------------------------------------------------------ Olhos de Águia

    /**
     * Agachado com o arco: os inimigos num raio aparecem para ele. O brilho (Glowing) é igual para todos os
     * jogadores na API do Paper, então aqui são partículas enviadas só para o Arqueiro, acima da cabeça do alvo.
     */
    private void eagleEyes() {
        double range = settings.eagleRange();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isSneaking() || !isArcher(player) || !holdsBow(player) || player.isDead()) {
                continue;
            }
            for (Entity nearby : player.getNearbyEntities(range, range, range)) {
                if (!targets.isEnemy(player, nearby)
                        || nearby.getLocation().distanceSquared(player.getLocation()) > range * range) {
                    continue;
                }
                LivingEntity living = (LivingEntity) nearby;
                Location above = living.getEyeLocation().add(0, 0.6, 0);
                player.spawnParticle(Particle.DUST, above, 3, 0.1, 0.1, 0.1, 0, EAGLE_DUST);
                player.spawnParticle(Particle.DUST, living.getLocation().add(0, living.getHeight() / 2, 0), 4,
                        living.getWidth() / 3, living.getHeight() / 3, living.getWidth() / 3, 0, EAGLE_DUST);
            }
        }
    }

    // ------------------------------------------------------------ dica das teclas

    /** Ao passar a segurar o arco, mostra a dica por ~3s (mensagens de habilidade têm prioridade). */
    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        ItemStack item = player.getInventory().getItem(event.getNewSlot());
        if (!isArcher(player) || !auth.isLoggedIn(id) || focus.warnedRecently(id) || !settings.isArcherBow(item)) {
            return;
        }
        player.sendActionBar(ShopUi.text(TIP));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastClick.remove(event.getPlayer().getUniqueId());
    }
}
