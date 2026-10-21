package br.com.murilo.muriloplug.classes.suporte;

import br.com.murilo.muriloplug.auth.AuthManager;
import br.com.murilo.muriloplug.clan.ClanManager;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.List;

/** Mira e grupos do Suporte. "Aliado" = membro do mesmo clan. */
public final class SuporteTargets {

    private final AuthManager auth;
    private final ClanManager clans;

    public SuporteTargets(AuthManager auth, ClanManager clans) {
        this.auth = auth;
        this.clans = clans;
    }

    public boolean isAlly(Player a, Player b) {
        return !a.equals(b) && clans.isSameClan(a.getUniqueId(), b.getUniqueId());
    }

    /** Primeiro jogador na mira (vivo, logado, fora do espectador) até {@code range} blocos, sem atravessar parede. */
    public Player aimedPlayer(Player from, double range) {
        Location eye = from.getEyeLocation();
        RayTraceResult result = from.getWorld().rayTrace(eye, eye.getDirection(), range, FluidCollisionMode.NEVER,
                true, 0.3, entity -> entity instanceof Player other && !other.equals(from) && !other.isDead()
                        && other.getGameMode() != GameMode.SPECTATOR && auth.isLoggedIn(other.getUniqueId()));
        return result != null && result.getHitEntity() instanceof Player hit ? hit : null;
    }

    /** O próprio Suporte e os aliados vivos a até {@code radius} blocos dele. */
    public List<Player> group(Player support, double radius) {
        List<Player> found = new ArrayList<>();
        found.add(support);
        Location center = support.getLocation();
        for (Player other : center.getWorld().getNearbyPlayers(center, radius)) {
            if (isAlly(support, other) && !other.isDead() && other.getGameMode() != GameMode.SPECTATOR
                    && auth.isLoggedIn(other.getUniqueId()) && other.getLocation().distance(center) <= radius) {
                found.add(other);
            }
        }
        return found;
    }
}
