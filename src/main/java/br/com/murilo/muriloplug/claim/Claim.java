package br.com.murilo.muriloplug.claim;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Área retangular no plano X/Z, sem limite de altura. Os limites são inclusivos. */
public record Claim(UUID owner, String name, String world, int minX, int minZ, int maxX, int maxZ,
                    List<UUID> trusted) {

    public static final int MAX_SIZE = 50;

    public Claim {
        trusted = trusted == null ? List.of() : List.copyOf(trusted);
    }

    public Claim(UUID owner, String name, String world, int minX, int minZ, int maxX, int maxZ) {
        this(owner, name, world, minX, minZ, maxX, maxZ, List.of());
    }

    /** Cria um claim quadrado de {@code size}x{@code size} blocos, ainda sem nome, centrado na localização. */
    public static Claim centeredOn(UUID owner, Location center, int size) {
        int half = size / 2;
        int minX = center.getBlockX() - half;
        int minZ = center.getBlockZ() - half;
        return new Claim(owner, "", center.getWorld().getName(), minX, minZ, minX + size - 1, minZ + size - 1);
    }

    public Claim withName(String newName) {
        return new Claim(owner, newName, world, minX, minZ, maxX, maxZ, trusted);
    }

    public Claim withTrusted(UUID player) {
        List<UUID> list = new ArrayList<>(trusted);
        list.add(player);
        return new Claim(owner, name, world, minX, minZ, maxX, maxZ, list);
    }

    public Claim withoutTrusted(UUID player) {
        List<UUID> list = new ArrayList<>(trusted);
        list.remove(player);
        return new Claim(owner, name, world, minX, minZ, maxX, maxZ, list);
    }

    public boolean isTrusted(UUID player) {
        return trusted.contains(player);
    }

    /** O dono e os jogadores de confiança podem construir no claim. */
    public boolean canBuild(UUID player) {
        return owner.equals(player) || isTrusted(player);
    }

    /** Nomes dos jogadores de confiança (usa o nome conhecido pelo servidor). */
    public List<String> trustedNames() {
        List<String> names = new ArrayList<>();
        for (UUID id : trusted) {
            String playerName = Bukkit.getOfflinePlayer(id).getName();
            names.add(playerName != null ? playerName : id.toString());
        }
        return names;
    }

    public int width() {
        return maxX - minX + 1;
    }

    public int depth() {
        return maxZ - minZ + 1;
    }

    public boolean contains(Location location) {
        return location.getWorld() != null
                && location.getWorld().getName().equals(world)
                && location.getBlockX() >= minX && location.getBlockX() <= maxX
                && location.getBlockZ() >= minZ && location.getBlockZ() <= maxZ;
    }

    public boolean overlaps(Claim other) {
        return world.equals(other.world)
                && minX <= other.maxX && maxX >= other.minX
                && minZ <= other.maxZ && maxZ >= other.minZ;
    }
}
