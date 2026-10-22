package br.com.murilo.muriloplug.classes.suporte;

import br.com.murilo.muriloplug.auth.AuthManager;
import br.com.murilo.muriloplug.teste.TargetRelations;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.List;

/** Mira e grupos do Suporte. "Aliado" = membro do mesmo clan (ou boneco de teste aliado). */
public final class SuporteTargets {

    private final AuthManager auth;
    private final TargetRelations relations;

    public SuporteTargets(AuthManager auth, TargetRelations relations) {
        this.auth = auth;
        this.relations = relations;
    }

    public boolean isAlly(Player a, LivingEntity b) {
        return relations.isAlly(a, b);
    }

    /**
     * Primeiro alvo na mira até {@code range} blocos, sem atravessar parede: um jogador (vivo, logado, fora do
     * espectador) ou um boneco de teste.
     */
    public LivingEntity aimedTarget(Player from, double range) {
        Location eye = from.getEyeLocation();
        RayTraceResult result = from.getWorld().rayTrace(eye, eye.getDirection(), range, FluidCollisionMode.NEVER,
                true, 0.3, entity -> isTargetable(from, entity));
        return result != null && result.getHitEntity() instanceof LivingEntity hit ? hit : null;
    }

    private boolean isTargetable(Player from, Entity entity) {
        if (entity.equals(from) || !(entity instanceof LivingEntity living) || living.isDead()) {
            return false;
        }
        if (entity instanceof Player other) {
            return other.getGameMode() != GameMode.SPECTATOR && auth.isLoggedIn(other.getUniqueId());
        }
        return relations.isDummy(entity);
    }

    /** O próprio Suporte e os aliados vivos (jogadores e bonecos aliados) a até {@code radius} blocos dele. */
    public List<LivingEntity> group(Player support, double radius) {
        List<LivingEntity> found = new ArrayList<>();
        found.add(support);
        Location center = support.getLocation();
        for (Player other : center.getWorld().getNearbyPlayers(center, radius)) {
            if (isAlly(support, other) && !other.isDead() && other.getGameMode() != GameMode.SPECTATOR
                    && auth.isLoggedIn(other.getUniqueId()) && other.getLocation().distance(center) <= radius) {
                found.add(other);
            }
        }
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius,
                e -> !(e instanceof Player) && e instanceof LivingEntity && relations.isAlly(support, (LivingEntity) e))) {
            LivingEntity dummy = (LivingEntity) entity;
            if (!dummy.isDead() && dummy.getLocation().distance(center) <= radius) {
                found.add(dummy);
            }
        }
        return found;
    }
}
