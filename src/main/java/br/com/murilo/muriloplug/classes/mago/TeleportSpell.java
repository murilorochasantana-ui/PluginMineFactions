package br.com.murilo.muriloplug.classes.mago;

import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/** Staff de Teleporte: teleporta na direção da mira parando ANTES do primeiro bloco, em um lugar seguro. */
final class TeleportSpell {

    /** Altura dos olhos de um jogador em pé. */
    private static final double EYE_HEIGHT = 1.62;
    private static final long NO_FALL_MILLIS = 3_000L;
    private static final int[][] RING = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}};
    private static final Set<Material> DANGEROUS = Set.of(
            Material.FIRE, Material.SOUL_FIRE, Material.LAVA, Material.POWDER_SNOW, Material.COBWEB,
            Material.MAGMA_BLOCK, Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.CACTUS,
            Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE, Material.POINTED_DRIPSTONE, Material.END_PORTAL,
            Material.NETHER_PORTAL, Material.END_GATEWAY);

    private final MagoSettings settings;
    private final Predicate<Player> canBeCarried;
    /** Quem acabou de pousar de um teleporte não leva dano de queda até este momento (ms). */
    private final Map<UUID, Long> noFallUntil = new HashMap<>();

    TeleportSpell(MagoSettings settings, Predicate<Player> canBeCarried) {
        this.settings = settings;
        this.canBeCarried = canBeCarried;
    }

    /** True se o pouso deste jogador ainda está protegido contra dano de queda. */
    boolean hasFallProtection(UUID player) {
        Long until = noFallUntil.get(player);
        if (until == null) {
            return false;
        }
        if (until < System.currentTimeMillis()) {
            noFallUntil.remove(player);
            return false;
        }
        return true;
    }

    void forget(UUID player) {
        noFallUntil.remove(player);
    }

    /** Retorna false (sem gastar nada) se não houver lugar seguro. */
    boolean cast(Player mage, boolean withCompanions) {
        Location destination = findDestination(mage);
        if (destination == null) {
            return false;
        }
        List<Player> companions = withCompanions ? companions(mage) : List.of();

        World world = mage.getWorld();
        Location origin = mage.getLocation();
        List<Location> spots = companionSpots(destination, companions.size());

        world.spawnParticle(Particle.PORTAL, origin.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.5);
        move(mage, destination);
        for (int i = 0; i < companions.size(); i++) {
            Player companion = companions.get(i);
            Location spot = i < spots.size() ? spots.get(i) : destination.clone();
            spot.setYaw(companion.getLocation().getYaw());
            spot.setPitch(companion.getLocation().getPitch());
            world.spawnParticle(Particle.PORTAL, companion.getLocation().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.5);
            move(companion, spot);
        }
        return true;
    }

    private void move(Player player, Location destination) {
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }
        player.teleport(destination);
        player.setFallDistance(0);
        noFallUntil.put(player.getUniqueId(), System.currentTimeMillis() + NO_FALL_MILLIS);
        World world = destination.getWorld();
        world.spawnParticle(Particle.PORTAL, destination.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.5);
        world.playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
    }

    /** Os jogadores mais próximos do Mago (até o máximo do config), qualquer um, aliado ou inimigo. */
    private List<Player> companions(Player mage) {
        double distance = settings.staff(StaffType.TELEPORTE, "distancia-acompanhantes");
        int max = settings.staffInt(StaffType.TELEPORTE, "max-acompanhantes");
        List<Player> found = new ArrayList<>();
        for (Player other : mage.getWorld().getPlayers()) {
            if (!other.equals(mage) && other.getGameMode() != GameMode.SPECTATOR && canBeCarried.test(other)
                    && other.getLocation().distanceSquared(mage.getLocation()) <= distance * distance) {
                found.add(other);
            }
        }
        found.sort(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(mage.getLocation())));
        return found.size() > max ? new ArrayList<>(found.subList(0, max)) : found;
    }

    /** Um lugar seguro ao lado do destino para cada acompanhante (pode devolver menos lugares do que o pedido). */
    private List<Location> companionSpots(Location destination, int count) {
        List<Location> spots = new ArrayList<>();
        Set<String> used = new HashSet<>();
        World world = destination.getWorld();
        for (int[] offset : RING) {
            if (spots.size() >= count) {
                break;
            }
            int x = destination.getBlockX() + offset[0];
            int z = destination.getBlockZ() + offset[1];
            for (int dy : new int[]{0, 1, -1}) {
                int y = destination.getBlockY() + dy;
                if (isSafe(world, x, y, z) && used.add(x + ":" + z)) {
                    spots.add(new Location(world, x + 0.5, y, z + 0.5));
                    break;
                }
            }
        }
        return spots;
    }

    /** Lugar seguro mais longe possível ao longo da mira, sempre ANTES do primeiro bloco sólido. */
    private Location findDestination(Player mage) {
        World world = mage.getWorld();
        Location eye = mage.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        double range = settings.staff(StaffType.TELEPORTE, "alcance");

        RayTraceResult hit = world.rayTraceBlocks(eye, direction, range, FluidCollisionMode.NEVER, true);
        double distance = hit == null ? range : hit.getHitPosition().distance(eye.toVector()) - 0.4;

        for (double d = distance; d >= 1.0; d -= 0.5) {
            Vector point = eye.toVector().add(direction.clone().multiply(d));
            int x = point.getBlockX();
            int z = point.getBlockZ();
            int baseY = (int) Math.floor(point.getY() - EYE_HEIGHT);
            for (int dy : new int[]{0, 1, -1, -2, -3}) {
                int y = baseY + dy;
                if (isSafe(world, x, y, z)) {
                    Location spot = new Location(world, x + 0.5, y, z + 0.5, eye.getYaw(), eye.getPitch());
                    return world.getWorldBorder().isInside(spot) ? spot : null;
                }
            }
        }
        return null;
    }

    /** Dois blocos de ar para o corpo, chão sólido embaixo e nada perigoso (lava, fogo, cacto...). */
    private static boolean isSafe(World world, int x, int y, int z) {
        if (y - 1 < world.getMinHeight() || y + 1 >= world.getMaxHeight()) {
            return false;
        }
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block ground = world.getBlockAt(x, y - 1, z);
        return bodySpace(feet) && bodySpace(head) && ground.getType().isSolid()
                && !DANGEROUS.contains(ground.getType());
    }

    private static boolean bodySpace(Block block) {
        return block.isPassable() && !block.isLiquid() && !DANGEROUS.contains(block.getType());
    }
}
