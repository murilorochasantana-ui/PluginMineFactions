package br.com.murilo.muriloplug.teste;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Zombie;

import java.util.ArrayDeque;
import java.util.Deque;

/** Um boneco de teste: o que é salvo em bonecos.yml (tipo, posição original, armadura) e o estado em execução. */
final class Dummy {

    final TargetRelations.DummyType type;
    final String world;
    final double x;
    final double y;
    final double z;
    final float yaw;
    final float pitch;
    /** "nenhuma", "couro", "ferro", "diamante" ou "netherite". */
    String armor;

    Zombie entity;
    TextDisplay display;
    /** Último texto enviado ao display (só reenvia quando muda). */
    String lastText = "";
    /** Posição na última conferência, para saber se o boneco está parado. */
    Location lastPosition;
    long stillSince;
    long fullSince;
    double lastHealth;
    /** Dano recebido: {momento (ms), dano}. */
    final Deque<double[]> recent = new ArrayDeque<>();

    Dummy(TargetRelations.DummyType type, String world, double x, double y, double z, float yaw, float pitch, String armor) {
        this.type = type;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.armor = armor;
    }

    static Dummy at(TargetRelations.DummyType type, Location location, String armor) {
        return new Dummy(type, location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch(), armor);
    }

    /** Posição original (null se o mundo não existe mais). */
    Location origin() {
        World bukkitWorld = Bukkit.getWorld(world);
        return bukkitWorld == null ? null : new Location(bukkitWorld, x, y, z, yaw, pitch);
    }

    /** As 4 peças (capacete, peitoral, calça, botas) da armadura escolhida, ou null se for "nenhuma". */
    static Material[] armorSet(String armor) {
        String prefix = switch (armor) {
            case "couro" -> "LEATHER";
            case "ferro" -> "IRON";
            case "diamante" -> "DIAMOND";
            case "netherite" -> "NETHERITE";
            default -> null;
        };
        if (prefix == null) {
            return null;
        }
        return new Material[]{Material.valueOf(prefix + "_HELMET"), Material.valueOf(prefix + "_CHESTPLATE"),
                Material.valueOf(prefix + "_LEGGINGS"), Material.valueOf(prefix + "_BOOTS")};
    }
}
