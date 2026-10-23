package br.com.murilo.muriloplug.classes.engenheiro;

import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.GrapplePull;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.classes.arqueiro.ArrowItems;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Flecha Gancho: só Engenheiros, só na besta. Ao acertar um bloco (até a distância máxima) puxa o Engenheiro até o
 * ponto de impacto; ao acertar uma entidade só dá o dano normal. Tem recarga entre ganchos, a flecha não pode ser
 * recolhida e não há dano de queda por alguns segundos depois da puxada.
 */
public final class GanchoManager implements Listener {

    private static final long WARNING_COOLDOWN_MILLIS = 2_000L;

    private final JavaPlugin plugin;
    private final ClassManager classes;
    private final EngenheiroSettings settings;
    private final ArrowItems arrows;
    private final EngenheiroAcoes actions;
    private final GrapplePull pull;
    /** Momento (ms) do último tiro de gancho de cada Engenheiro. */
    private final Map<UUID, Long> lastShot = new HashMap<>();
    private final Map<UUID, Long> lastWarning = new HashMap<>();

    public GanchoManager(JavaPlugin plugin, ClassManager classes, EngenheiroSettings settings, ArrowItems arrows,
                         EngenheiroAcoes actions, GrapplePull pull) {
        this.pull = pull;
        this.plugin = plugin;
        this.classes = classes;
        this.settings = settings;
        this.arrows = arrows;
        this.actions = actions;
    }

    private boolean isEngineer(Player player) {
        return classes.hasClass(player.getUniqueId(), PlayerClass.ENGENHEIRO);
    }

    /** Segundos até poder atirar outro gancho (arredondados para cima), ou 0. */
    public int remainingSeconds(UUID id) {
        Long last = lastShot.get(id);
        if (last == null) {
            return 0;
        }
        long left = last + settings.grappleCooldownSeconds() * 1000L - System.currentTimeMillis();
        return left <= 0 ? 0 : (int) Math.ceil(left / 1000.0);
    }

    private void warn(Player player, String message) {
        long now = System.currentTimeMillis();
        Long last = lastWarning.get(player.getUniqueId());
        if (last == null || now - last >= WARNING_COOLDOWN_MILLIS) {
            lastWarning.put(player.getUniqueId(), now);
            player.sendMessage(message);
        }
    }

    // ------------------------------------------------------------ o tiro

    /** Cancela (sem gastar a flecha) o gancho fora da besta, de outra classe ou em recarga; marca o que valer. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || !arrows.isGrapple(event.getConsumable())) {
            return;
        }
        UUID id = player.getUniqueId();
        boolean crossbow = event.getBow() != null && event.getBow().getType() == Material.CROSSBOW;
        if (!isEngineer(player) || !crossbow) {
            deny(event, player, "§cA Flecha Gancho só funciona na besta e só para Engenheiros.");
            return;
        }
        int wait = remainingSeconds(id);
        if (wait > 0) {
            deny(event, player, "§cGancho em recarga: " + wait + "s");
            return;
        }
        lastShot.put(id, System.currentTimeMillis());
        if (event.getProjectile() instanceof AbstractArrow arrow) {
            arrows.markGrappleProjectile(arrow.getPersistentDataContainer());
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        }
    }

    private void deny(EntityShootBowEvent event, Player player, String message) {
        event.setCancelled(true);
        player.updateInventory();
        warn(player, message);
    }

    // ------------------------------------------------------------ o impacto

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow)
                || !arrows.isGrappleProjectile(arrow.getPersistentDataContainer())) {
            return;
        }
        if (event.getHitBlock() != null && arrow.getShooter() instanceof Player shooter && shooter.isOnline()
                && !shooter.isDead() && isEngineer(shooter) && shooter.getWorld().equals(arrow.getWorld())) {
            Location impact = arrow.getLocation();
            if (shooter.getLocation().distance(impact) > settings.grappleRange()) {
                warn(shooter, "§cAlvo longe demais (máximo " + (int) settings.grappleRange() + " blocos).");
            } else {
                pull.pull(shooter, GrapplePull.target(impact, event.getHitBlockFace()), settings.grappleSpeed(),
                        settings.grappleFallProtectionSeconds());
                actions.registrarAcao(shooter);
            }
        }
        // A flecha some depois de usada; no tick seguinte, para o dano de quem foi atingido ser resolvido antes.
        plugin.getServer().getScheduler().runTask(plugin, arrow::remove);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        lastShot.remove(id);
        lastWarning.remove(id);
    }
}
