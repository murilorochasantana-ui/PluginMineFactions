package br.com.murilo.muriloplug.home;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public record Home(String name, String world, double x, double y, double z, float yaw, float pitch) {

    public static Home at(String name, Location location) {
        return new Home(name, location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
    }

    public Home withName(String newName) {
        return new Home(newName, world, x, y, z, yaw, pitch);
    }

    /** Localização da home, ou null se o mundo não existir mais. */
    public Location toLocation() {
        World bukkitWorld = Bukkit.getWorld(world);
        return bukkitWorld == null ? null : new Location(bukkitWorld, x, y, z, yaw, pitch);
    }
}
