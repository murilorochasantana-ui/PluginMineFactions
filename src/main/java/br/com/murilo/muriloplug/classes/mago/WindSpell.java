package br.com.murilo.muriloplug.classes.mago;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/** Staff de Vento: empurra para longe e causa dano em tudo ao redor do Mago. */
final class WindSpell {

    private final SpellTargets targets;
    private final MagoSettings settings;

    WindSpell(SpellTargets targets, MagoSettings settings) {
        this.targets = targets;
        this.settings = settings;
    }

    boolean cast(Player mage) {
        StaffType type = StaffType.VENTO;
        double radius = settings.staff(type, "raio");
        double damage = settings.staff(type, "dano");
        double horizontal = settings.staff(type, "forca-horizontal");
        double vertical = settings.staff(type, "forca-vertical");

        Location center = mage.getLocation();
        for (LivingEntity target : targets.nearby(mage, center, radius)) {
            targets.damage(mage, target, damage);
            Vector push = target.getLocation().toVector().subtract(center.toVector()).setY(0);
            if (push.lengthSquared() < 0.0001) {
                double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
                push = new Vector(Math.cos(angle), 0, Math.sin(angle));
            }
            // Depois do dano, que também aplica um empurrão próprio.
            target.setVelocity(push.normalize().multiply(horizontal).setY(vertical));
        }

        World world = mage.getWorld();
        Location middle = center.clone().add(0, 1, 0);
        world.spawnParticle(Particle.CLOUD, middle, 60, radius / 3, 0.4, radius / 3, 0.15);
        world.spawnParticle(Particle.GUST, middle, 3, radius / 4, 0.3, radius / 4, 0);
        world.playSound(center, Sound.ENTITY_BREEZE_WIND_BURST, 1f, 1f);
        return true;
    }
}
