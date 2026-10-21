package br.com.murilo.muriloplug.classes.mago;

import br.com.murilo.muriloplug.auth.AuthManager;
import br.com.murilo.muriloplug.clan.ClanManager;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Regras comuns das magias: quem pode ser afetado, como causar o dano e linha de visão. */
public final class SpellTargets {

    private final AuthManager auth;
    private final ClanManager clans;
    private final ArcanePower arcane;

    public SpellTargets(AuthManager auth, ClanManager clans, ArcanePower arcane) {
        this.auth = auth;
        this.clans = clans;
        this.arcane = arcane;
    }

    /** Só jogadores e mobs; nunca o Mago, nem o clan dele, nem jogadores não logados. */
    public boolean canAffect(Player mage, Entity entity) {
        if (!(entity instanceof LivingEntity living) || entity instanceof ArmorStand
                || entity.equals(mage) || living.isDead() || !living.isValid()) {
            return false;
        }
        if (entity instanceof Player player) {
            return auth.isLoggedIn(player.getUniqueId())
                    && player.getGameMode() != GameMode.SPECTATOR
                    && !clans.isSameClan(mage.getUniqueId(), player.getUniqueId());
        }
        return true;
    }

    /** Dano mágico indireto (ignora armadura, respeita Proteção) causado pelo Mago, com o bônus do Poder Arcano. */
    public void damage(Player mage, LivingEntity target, double baseAmount) {
        double amount = baseAmount * arcane.multiplier(mage.getUniqueId());
        DamageSource source = DamageSource.builder(DamageType.INDIRECT_MAGIC)
                .withCausingEntity(mage)
                .withDirectEntity(mage)
                .build();
        target.damage(amount, source);
    }

    /** Alvos possíveis a até {@code radius} blocos do centro (distância até o centro do corpo), do mais perto ao mais longe. */
    public List<LivingEntity> nearby(Player mage, Location center, double radius) {
        List<LivingEntity> found = new ArrayList<>();
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (canAffect(mage, entity) && bodyCenter(entity).distanceSquared(center) <= radius * radius) {
                found.add((LivingEntity) entity);
            }
        }
        found.sort(Comparator.comparingDouble(e -> bodyCenter(e).distanceSquared(center)));
        return found;
    }

    public static Location bodyCenter(Entity entity) {
        return entity.getBoundingBox().getCenter().toLocation(entity.getWorld());
    }

    /** True se não há bloco sólido entre os dois pontos. */
    public static boolean clearBetween(Location from, Location to) {
        Vector direction = to.toVector().subtract(from.toVector());
        double distance = direction.length();
        if (distance < 0.01) {
            return true;
        }
        return from.getWorld().rayTraceBlocks(from, direction.normalize(), distance, FluidCollisionMode.NEVER, true) == null;
    }

    /** Linha de partículas entre dois pontos. */
    public static void line(World world, Location from, Location to, Particle particle) {
        Vector direction = to.toVector().subtract(from.toVector());
        double length = direction.length();
        if (length < 0.01) {
            return;
        }
        direction.normalize().multiply(0.4);
        Location point = from.clone();
        for (double travelled = 0; travelled < length; travelled += 0.4) {
            world.spawnParticle(particle, point, 1, 0, 0, 0, 0);
            point.add(direction);
        }
    }
}
