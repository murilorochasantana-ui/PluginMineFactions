package br.com.murilo.muriloplug.classes.engenheiro;

import org.bukkit.World;

import java.util.List;
import java.util.UUID;

/**
 * Uma construção aberta: a área marcada (chão + volume acima dele) e o chão original, guardado como
 * "x z blockdata" para ser devolvido ao terminar.
 */
final class OpenBuild {

    final UUID owner;
    final String ownerName;
    final UUID bookId;
    final String world;
    final int floorY;
    final int minX;
    final int maxX;
    final int minZ;
    final int maxZ;
    final int height;
    final long openedAt;
    final List<String> floor;

    OpenBuild(UUID owner, String ownerName, UUID bookId, String world, int floorY, int minX, int maxX, int minZ,
              int maxZ, int height, long openedAt, List<String> floor) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.bookId = bookId;
        this.world = world;
        this.floorY = floorY;
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.height = height;
        this.openedAt = openedAt;
        this.floor = floor;
    }

    int topY() {
        return floorY + height;
    }

    /** Se a posição está no chão ou no volume desta construção. */
    boolean contains(World w, int x, int y, int z) {
        return w != null && w.getName().equals(world) && x >= minX && x <= maxX && z >= minZ && z <= maxZ
                && y >= floorY && y <= topY();
    }

    boolean isFloor(int y) {
        return y == floorY;
    }

    /** Se as áreas se tocam ou se sobrepõem (com {@code margin} blocos de folga em cada direção). */
    boolean touches(String otherWorld, int oMinX, int oMaxX, int oMinZ, int oMaxZ, int oFloorY, int oTopY, int margin) {
        return world.equals(otherWorld)
                && oMinX - margin <= maxX && oMaxX + margin >= minX
                && oMinZ - margin <= maxZ && oMaxZ + margin >= minZ
                && oFloorY - margin <= topY() && oTopY + margin >= floorY;
    }
}
