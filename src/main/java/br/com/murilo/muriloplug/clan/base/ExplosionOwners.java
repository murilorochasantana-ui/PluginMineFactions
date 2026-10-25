package br.com.murilo.muriloplug.clan.base;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Dispenser;
import org.bukkit.block.Dropper;
import org.bukkit.block.TileState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Descobre quem causou uma explosão de TNT (para a recompensa do Núcleo e para o registro de raids):
 * <ol>
 *   <li>TNT acesa por um jogador (isqueiro, flecha de fogo): o {@link TNTPrimed#getSource()} já é ele;</li>
 *   <li>TNT colocada por um jogador e acesa depois (redstone, fogo): guardamos quem colocou cada bloco de TNT e
 *       passamos para a TNT acesa;</li>
 *   <li>canhão com dispenser: o dispenser guarda o "dono" no PersistentDataContainer (quem o colocou, ou o
 *       Engenheiro que construiu a estrutura); a TNT lançada herda o dono. Sem dono, vale quem apertou um botão,
 *       alavanca ou placa perto dele nos últimos 30 segundos;</li>
 *   <li>reação em cadeia: a TNT acesa por outra explosão herda o dono da primeira.</li>
 * </ol>
 * O dono fica gravado na própria TNT acesa ({@code mineplug:tnt_dono}).
 */
public final class ExplosionOwners implements Listener {

    private static final long PLACED_TTL_MILLIS = 2 * 60 * 60 * 1000L;
    private static final long PRIMED_TTL_MILLIS = 2_000L;
    private static final long REDSTONE_TTL_MILLIS = 30_000L;
    private static final double REDSTONE_RADIUS = 24;
    private static final int MAX_PLACED = 20_000;

    private record Mark(UUID owner, long time) {
    }

    private record RedstoneUse(UUID player, Location location, long time) {
    }

    private static ExplosionOwners instance;

    private final NamespacedKey tntKey;
    private final NamespacedKey dispenserKey;
    /** Bloco de TNT -> quem colocou. */
    private final Map<BaseLoc, Mark> placedTnt = new HashMap<>();
    /** Bloco onde uma TNT acabou de ser acesa (a entidade nasce logo depois) -> dono. */
    private final Map<BaseLoc, Mark> primed = new HashMap<>();
    /** Dispenser que acabou de lançar TNT -> dono. */
    private final Map<BaseLoc, Mark> dispensed = new HashMap<>();
    private final Deque<RedstoneUse> redstoneUses = new ArrayDeque<>();

    public ExplosionOwners(JavaPlugin plugin) {
        this.tntKey = new NamespacedKey(plugin, "tnt_dono");
        this.dispenserKey = new NamespacedKey(plugin, "dono");
        instance = this;
    }

    /** Marca o dono de um dispenser/dropper (usado pelas estruturas do Engenheiro). Sem efeito em outros blocos. */
    public static void markOwner(Block block, UUID owner) {
        ExplosionOwners owners = instance;
        if (owners != null && owner != null) {
            owners.setDispenserOwner(block, owner);
        }
    }

    /** Jogador que causou a explosão desta entidade, ou null se não der para saber. */
    public UUID ownerOf(Entity entity) {
        if (entity == null) {
            return null;
        }
        String stored = entity.getPersistentDataContainer().get(tntKey, PersistentDataType.STRING);
        if (stored != null) {
            try {
                return UUID.fromString(stored);
            } catch (IllegalArgumentException ignored) {
                // marca estragada: segue procurando
            }
        }
        if (entity instanceof TNTPrimed tnt && tnt.getSource() != null) {
            Entity source = tnt.getSource();
            if (source instanceof Player player) {
                return player.getUniqueId();
            }
            if (source instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
                return shooter.getUniqueId();
            }
            if (source != entity && source.getPersistentDataContainer().has(tntKey, PersistentDataType.STRING)) {
                return ownerOf(source);
            }
        }
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter.getUniqueId();
        }
        return null;
    }

    // ------------------------------------------------------------ eventos

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        UUID player = event.getPlayer().getUniqueId();
        if (block.getType() == Material.TNT) {
            if (placedTnt.size() > MAX_PLACED) {
                prune(placedTnt, PLACED_TTL_MILLIS);
            }
            placedTnt.put(BaseLoc.of(block), new Mark(player, System.currentTimeMillis()));
        } else if (block.getType() == Material.DISPENSER || block.getType() == Material.DROPPER) {
            setDispenserOwner(block, player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.TNT) {
            placedTnt.remove(BaseLoc.of(event.getBlock()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRedstoneUse(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        boolean click = event.getAction() == Action.RIGHT_CLICK_BLOCK
                && (Tag.BUTTONS.isTagged(block.getType()) || block.getType() == Material.LEVER);
        boolean step = event.getAction() == Action.PHYSICAL && Tag.PRESSURE_PLATES.isTagged(block.getType());
        if (!click && !step) {
            return;
        }
        long now = System.currentTimeMillis();
        redstoneUses.addLast(new RedstoneUse(event.getPlayer().getUniqueId(), block.getLocation(), now));
        while (redstoneUses.size() > 64 || (!redstoneUses.isEmpty() && now - redstoneUses.peekFirst().time() > REDSTONE_TTL_MILLIS)) {
            redstoneUses.pollFirst();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPrime(TNTPrimeEvent event) {
        BaseLoc key = BaseLoc.of(event.getBlock());
        Mark placed = placedTnt.remove(key);
        UUID owner = null;
        Entity priming = event.getPrimingEntity();
        if (priming instanceof Player player) {
            owner = player.getUniqueId();
        } else if (priming != null) {
            owner = ownerOf(priming);
        }
        if (owner == null && event.getPrimingBlock() != null
                && (event.getPrimingBlock().getType() == Material.DISPENSER)) {
            owner = dispenserOwner(event.getPrimingBlock());
        }
        if (owner == null && placed != null) {
            owner = placed.owner();
        }
        if (owner != null) {
            prune(primed, PRIMED_TTL_MILLIS);
            primed.put(key, new Mark(owner, System.currentTimeMillis()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (event.getItem().getType() != Material.TNT) {
            return;
        }
        UUID owner = dispenserOwner(event.getBlock());
        if (owner != null) {
            prune(dispensed, PRIMED_TTL_MILLIS);
            dispensed.put(BaseLoc.of(event.getBlock()), new Mark(owner, System.currentTimeMillis()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed tnt)) {
            return;
        }
        UUID owner = null;
        if (tnt.getSource() instanceof Player player) {
            owner = player.getUniqueId();
        }
        if (owner == null) {
            Mark mark = primed.remove(BaseLoc.of(tnt.getLocation().getBlock()));
            owner = mark == null ? null : mark.owner();
        }
        if (owner == null) {
            owner = nearestDispenser(tnt.getLocation());
        }
        if (owner == null) {
            owner = ownerOf(tnt);
        }
        if (owner != null) {
            tnt.getPersistentDataContainer().set(tntKey, PersistentDataType.STRING, owner.toString());
        }
    }

    // ------------------------------------------------------------ auxiliares

    private void setDispenserOwner(Block block, UUID owner) {
        BlockState state = block.getState(false);
        if ((state instanceof Dispenser || state instanceof Dropper) && state instanceof TileState tile) {
            tile.getPersistentDataContainer().set(dispenserKey, PersistentDataType.STRING, owner.toString());
            tile.update(false, false);
        }
    }

    private UUID dispenserOwner(Block block) {
        if (block.getState(false) instanceof TileState tile) {
            String stored = tile.getPersistentDataContainer().get(dispenserKey, PersistentDataType.STRING);
            if (stored != null) {
                try {
                    return UUID.fromString(stored);
                } catch (IllegalArgumentException ignored) {
                    // marca estragada
                }
            }
        }
        // Sem dono marcado: quem mexeu na redstone por perto há pouco.
        long now = System.currentTimeMillis();
        Location center = block.getLocation();
        Iterator<RedstoneUse> iterator = redstoneUses.descendingIterator();
        while (iterator.hasNext()) {
            RedstoneUse use = iterator.next();
            if (now - use.time() <= REDSTONE_TTL_MILLIS && use.location().getWorld() == center.getWorld()
                    && use.location().distanceSquared(center) <= REDSTONE_RADIUS * REDSTONE_RADIUS) {
                return use.player();
            }
        }
        return null;
    }

    /** Dono de um dispenser que lançou TNT agora mesmo, a até 2 blocos da TNT nova. */
    private UUID nearestDispenser(Location location) {
        long now = System.currentTimeMillis();
        for (Map.Entry<BaseLoc, Mark> entry : dispensed.entrySet()) {
            if (now - entry.getValue().time() <= PRIMED_TTL_MILLIS && entry.getKey().distance(location) <= 2.0) {
                UUID owner = entry.getValue().owner();
                dispensed.remove(entry.getKey());
                return owner;
            }
        }
        return null;
    }

    private static void prune(Map<BaseLoc, Mark> map, long ttl) {
        long now = System.currentTimeMillis();
        map.values().removeIf(mark -> now - mark.time() > ttl);
    }
}
