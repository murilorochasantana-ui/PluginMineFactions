package br.com.murilo.muriloplug.classes.arqueiro;

import br.com.murilo.muriloplug.classes.SilenceManager;
import br.com.murilo.muriloplug.auth.AuthManager;
import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mobilidade do Arqueiro: Pulo Duplo (gasta Foco), Dash (Shift + F com o arco) e a passiva Pés Leves.
 *
 * <p>Pulo Duplo: o Arqueiro em sobrevivência/aventura fica com {@code allowFlight} ligado, assim o segundo toque
 * em PULAR no ar chega como {@link PlayerToggleFlightEvent}. O evento é sempre cancelado (ele nunca voa de verdade);
 * se estiver no ar, ainda sem o pulo extra e com Foco, vira um impulso para cima e um pouco para a frente.
 */
public final class ArcherMobility implements Listener {

    private static final long TICK_PERIOD = 2L;
    private static final double DASH_SPEED = 1.0;
    private static final int DASH_MAX_TICKS = 12;

    private final JavaPlugin plugin;
    private final ClassManager classes;
    private final ArqueiroSettings settings;
    private final FocusManager focus;
    private final AuthManager auth;
    /** Arqueiros que já usaram o pulo extra e ainda não tocaram o chão. */
    private final Set<UUID> jumped = new HashSet<>();
    /** Jogadores cujo {@code allowFlight} foi ligado por esta classe (para desligar depois). */
    private final Set<UUID> flightOwned = new HashSet<>();
    /** Fim (ms) da proteção contra queda depois do Dash. */
    private final Map<UUID, Long> fallProtectedUntil = new HashMap<>();
    private final Set<UUID> dashing = new HashSet<>();

    public ArcherMobility(JavaPlugin plugin, ClassManager classes, ArqueiroSettings settings, FocusManager focus,
                          AuthManager auth) {
        this.plugin = plugin;
        this.classes = classes;
        this.settings = settings;
        this.focus = focus;
        this.auth = auth;
    }

    private boolean isArcher(Player player) {
        return classes.hasClass(player.getUniqueId(), PlayerClass.ARQUEIRO);
    }

    private static boolean survivalLike(Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }

    // ------------------------------------------------------------ relógio do Pulo Duplo

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
    }

    /** Desliga o voo que esta classe ligou (onDisable). */
    public void removeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            releaseFlight(player);
        }
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (isArcher(player) && survivalLike(player)) {
                if (player.isOnGround()) {
                    jumped.remove(id);
                }
                if (!player.getAllowFlight()) {
                    player.setAllowFlight(true);
                    flightOwned.add(id);
                }
            } else if (flightOwned.contains(id)) {
                releaseFlight(player);
            }
        }
    }

    /** Tira o voo que nós ligamos, sem mexer em quem está em criativo/espectador (que voa por conta própria). */
    private void releaseFlight(Player player) {
        UUID id = player.getUniqueId();
        if (flightOwned.remove(id) && survivalLike(player)) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
        jumped.remove(id);
    }

    // ------------------------------------------------------------ Pulo Duplo

    @EventHandler(priority = EventPriority.HIGH)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!isArcher(player) || !survivalLike(player)) {
            return; // criativo e espectador voam normalmente
        }
        event.setCancelled(true);
        UUID id = player.getUniqueId();
        if (!event.isFlying() || player.isOnGround() || player.isInsideVehicle() || player.isGliding()
                || !auth.isLoggedIn(id) || jumped.contains(id) || SilenceManager.blocks(player)) {
            return;
        }
        int cost = settings.jumpCost();
        if (!focus.spend(id, cost)) {
            focus.warnInsufficient(player, cost);
            return;
        }
        jumped.add(id);
        Vector facing = player.getLocation().getDirection().setY(0);
        if (facing.lengthSquared() > 1.0E-4) {
            facing.normalize().multiply(settings.jumpForward());
        }
        Vector current = player.getVelocity();
        player.setVelocity(new Vector(current.getX() * 0.5 + facing.getX(), settings.jumpUp(),
                current.getZ() * 0.5 + facing.getZ()));
        Location feet = player.getLocation();
        player.getWorld().spawnParticle(Particle.CLOUD, feet, 10, 0.3, 0.05, 0.3, 0.02);
        player.getWorld().playSound(feet, Sound.ENTITY_BAT_TAKEOFF, 0.8f, 1.4f);
    }

    // ------------------------------------------------------------ Dash

    /** Shift + F com o arco na mão principal: Dash (a troca de mão é cancelada). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking() || !isArcher(player) || !settings.isArcherBow(player.getInventory().getItemInMainHand())) {
            return;
        }
        event.setCancelled(true);
        UUID id = player.getUniqueId();
        if (!auth.isLoggedIn(id) || dashing.contains(id) || SilenceManager.blocks(player)) {
            return;
        }
        int cost = settings.dashCost();
        if (!focus.spend(id, cost)) {
            focus.warnInsufficient(player, cost);
            return;
        }
        dash(player, player.getCurrentInput().isBackward());
    }

    /**
     * Avança {@code dashDistance} blocos na horizontal, na direção em que o jogador olha (ou para trás). A velocidade
     * é reaplicada a cada tick e o jogo cuida das colisões, então o Dash não atravessa paredes: se o jogador para de
     * andar, o Dash termina.
     */
    private void dash(Player player, boolean backward) {
        UUID id = player.getUniqueId();
        Vector direction = player.getLocation().getDirection().setY(0);
        if (direction.lengthSquared() < 1.0E-4) {
            double yaw = Math.toRadians(player.getLocation().getYaw());
            direction = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        }
        direction.normalize();
        if (backward) {
            direction.multiply(-1);
        }
        final Vector push = direction;
        double distance = settings.dashDistance();
        dashing.add(id);
        fallProtectedUntil.put(id, System.currentTimeMillis() + DASH_MAX_TICKS * 50L + settings.dashFallProtectionMillis());
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.3f);

        new BukkitRunnable() {
            Location last = player.getLocation();
            double moved;
            int elapsed;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || elapsed >= DASH_MAX_TICKS) {
                    finish();
                    return;
                }
                Location now = player.getLocation();
                if (elapsed > 0) {
                    double step = Math.hypot(now.getX() - last.getX(), now.getZ() - last.getZ());
                    moved += step;
                    // Bateu em uma parede (quase não saiu do lugar): acaba aqui.
                    if (moved >= distance || (elapsed > 1 && step < 0.05)) {
                        finish();
                        return;
                    }
                }
                last = now;
                elapsed++;
                double speed = Math.min(DASH_SPEED, distance - moved);
                player.setVelocity(new Vector(push.getX() * speed, player.getVelocity().getY(), push.getZ() * speed));
                player.getWorld().spawnParticle(Particle.CLOUD, now.clone().add(0, 0.3, 0), 4, 0.2, 0.2, 0.2, 0.02);
            }

            private void finish() {
                cancel();
                dashing.remove(id);
                if (player.isOnline()) {
                    Vector velocity = player.getVelocity();
                    player.setVelocity(new Vector(velocity.getX() * 0.2, velocity.getY(), velocity.getZ() * 0.2));
                }
                // Até 2s sem dano de queda a partir do fim do Dash.
                fallProtectedUntil.put(id, System.currentTimeMillis() + settings.dashFallProtectionMillis());
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ------------------------------------------------------------ queda: Dash e Pés Leves

    /** Sem dano de queda logo depois do Dash; Pés Leves: sem dano até certa altura e metade acima dela. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != DamageCause.FALL || !(event.getEntity() instanceof Player player) || !isArcher(player)) {
            return;
        }
        Long until = fallProtectedUntil.get(player.getUniqueId());
        if (until != null && until > System.currentTimeMillis()) {
            event.setCancelled(true);
            return;
        }
        if (player.getFallDistance() <= settings.featherSafeHeight()) {
            event.setCancelled(true);
        } else {
            event.setDamage(event.getDamage() * (1 - settings.featherReduction()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        releaseFlight(player);
        fallProtectedUntil.remove(player.getUniqueId());
        dashing.remove(player.getUniqueId());
    }
}
