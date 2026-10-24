package br.com.murilo.muriloplug.classes.suporte;

import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.BoundingBox;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Troca de Lugar (Shift + clique esquerdo com a Pena): o Suporte e o aliado vinculado trocam de posição, cada um
 * mantendo a direção da própria câmera. Só no mesmo mundo, até a distância máxima e com os dois destinos seguros
 * (espaço livre para o corpo, sem lava nem fogo). Os dois ficam sem dano de queda por alguns segundos.
 */
public final class TrocaLugar implements Listener {

    private final SuporteSettings settings;
    private final LinkManager links;
    /** Fim (ms) da proteção contra queda de cada entidade teleportada. */
    private final Map<UUID, Long> fallProtectedUntil = new HashMap<>();

    public TrocaLugar(SuporteSettings settings, LinkManager links) {
        this.settings = settings;
        this.links = links;
    }

    /** Troca os dois de lugar. Retorna false (sem gastar nada) se não deu. */
    public boolean swap(Player support) {
        LivingEntity ally = links.linkedAlly(support.getUniqueId());
        if (ally == null) {
            support.sendActionBar(ShopUi.text("§cVocê precisa estar vinculado a um aliado."));
            return false;
        }
        Location here = support.getLocation();
        Location there = ally.getLocation();
        if (!here.getWorld().equals(there.getWorld())) {
            support.sendActionBar(ShopUi.text("§cO aliado vinculado está em outro mundo."));
            return false;
        }
        if (here.distance(there) > settings.placeDistance()) {
            support.sendActionBar(ShopUi.text("§cO aliado está a mais de " + SuporteItems.format(settings.placeDistance())
                    + " blocos."));
            return false;
        }
        if (!isSafe(there, support) || !isSafe(here, ally)) {
            support.sendActionBar(ShopUi.text("§cO destino não é seguro."));
            return false;
        }
        // Cada um vai para a posição do outro, mantendo a própria direção da câmera.
        Location supportTarget = there.clone();
        supportTarget.setYaw(here.getYaw());
        supportTarget.setPitch(here.getPitch());
        Location allyTarget = here.clone();
        allyTarget.setYaw(there.getYaw());
        allyTarget.setPitch(there.getPitch());
        if (!support.teleport(supportTarget, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
            support.sendActionBar(ShopUi.text("§cNão foi possível trocar de lugar agora."));
            return false;
        }
        if (!ally.teleport(allyTarget, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
            support.teleport(here, PlayerTeleportEvent.TeleportCause.PLUGIN); // desfaz
            support.sendActionBar(ShopUi.text("§cNão foi possível trocar de lugar agora."));
            return false;
        }
        long protection = System.currentTimeMillis() + settings.placeFallProtectionSeconds() * 1000L;
        fallProtectedUntil.put(support.getUniqueId(), protection);
        fallProtectedUntil.put(ally.getUniqueId(), protection);
        effects(support);
        effects(ally);
        support.sendActionBar(ShopUi.text("§7Você trocou de lugar com o aliado vinculado."));
        if (ally instanceof Player allyPlayer) {
            allyPlayer.sendActionBar(ShopUi.text("§7" + support.getName() + " trocou de lugar com você."));
        }
        return true;
    }

    /** Espaço livre para o corpo de quem vai, sem lava nem fogo nos pés ou logo abaixo, e acima do fundo do mundo. */
    private static boolean isSafe(Location at, Entity mover) {
        World world = at.getWorld();
        if (at.getY() <= world.getMinHeight() || at.getY() >= world.getMaxHeight()) {
            return false;
        }
        double bottom = at.getY() + 0.01;
        double top = at.getY() + mover.getHeight() - 0.01;
        Block feet = at.getBlock();
        for (int y = feet.getY(); y <= (int) Math.floor(top); y++) {
            Block block = world.getBlockAt(feet.getX(), y, feet.getZ());
            if (isHazard(block.getType())) {
                return false;
            }
            // Laje, escada etc. embaixo do pé não atrapalham: só o que encosta no corpo.
            BoundingBox box = block.getBoundingBox();
            if (!block.isPassable() && box.getMaxY() > bottom && box.getMinY() < top) {
                return false;
            }
        }
        return !isHazard(feet.getRelative(0, -1, 0).getType());
    }

    private static boolean isHazard(Material material) {
        return material == Material.LAVA || material == Material.FIRE || material == Material.SOUL_FIRE
                || material == Material.MAGMA_BLOCK || material == Material.CAMPFIRE || material == Material.SOUL_CAMPFIRE
                || material == Material.SWEET_BERRY_BUSH || material == Material.WITHER_ROSE
                || material == Material.POWDER_SNOW || material == Material.CACTUS;
    }

    private static void effects(Entity entity) {
        Location center = entity.getLocation().add(0, 1, 0);
        entity.getWorld().spawnParticle(Particle.PORTAL, center, 50, 0.4, 0.8, 0.4, 0.5);
        entity.getWorld().spawnParticle(Particle.REVERSE_PORTAL, center, 20, 0.3, 0.6, 0.3, 0.05);
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.1f);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        Long until = fallProtectedUntil.get(event.getEntity().getUniqueId());
        if (until == null) {
            return;
        }
        if (until > System.currentTimeMillis()) {
            event.setCancelled(true);
        } else {
            fallProtectedUntil.remove(event.getEntity().getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        fallProtectedUntil.remove(event.getPlayer().getUniqueId());
    }
}
