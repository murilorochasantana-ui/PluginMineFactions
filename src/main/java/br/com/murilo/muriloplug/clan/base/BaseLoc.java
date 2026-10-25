package br.com.murilo.muriloplug.clan.base;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/** Posição de um bloco (mundo + x, y, z), segura para guardar em mapa e no arquivo. */
public record BaseLoc(String world, int x, int y, int z) {

    public static BaseLoc of(Block block) {
        return new BaseLoc(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    /** "mundo;x;y;z" (formato do arquivo), ou null se for inválido. */
    public static BaseLoc parse(String text) {
        if (text == null) {
            return null;
        }
        String[] parts = text.split(";");
        if (parts.length != 4) {
            return null;
        }
        try {
            return new BaseLoc(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String serialize() {
        return world + ";" + x + ";" + y + ";" + z;
    }

    /** Mundo carregado, ou null. */
    public World bukkitWorld() {
        return Bukkit.getWorld(world);
    }

    /** O bloco (null se o mundo não estiver carregado). */
    public Block block() {
        World w = bukkitWorld();
        return w == null ? null : w.getBlockAt(x, y, z);
    }

    /** Centro do bloco (null se o mundo não estiver carregado). */
    public Location center() {
        World w = bukkitWorld();
        return w == null ? null : new Location(w, x + 0.5, y + 0.5, z + 0.5);
    }

    public boolean isLoaded() {
        World w = bukkitWorld();
        return w != null && w.isChunkLoaded(x >> 4, z >> 4);
    }

    /** Distância do centro do bloco até a localização (infinita se for outro mundo). */
    public double distance(Location location) {
        if (location.getWorld() == null || !location.getWorld().getName().equals(world)) {
            return Double.POSITIVE_INFINITY;
        }
        double dx = location.getX() - (x + 0.5);
        double dy = location.getY() - (y + 0.5);
        double dz = location.getZ() - (z + 0.5);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public String coords() {
        return x + ", " + y + ", " + z;
    }
}
