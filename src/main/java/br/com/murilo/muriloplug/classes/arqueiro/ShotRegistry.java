package br.com.murilo.muriloplug.classes.arqueiro;

import org.bukkit.Location;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Dados das flechas do Arqueiro que estão no ar ou no chão (só em memória; somem quando a flecha some). */
public final class ShotRegistry implements Listener {

    public enum Kind {
        /** A flecha do tiro normal (também a primeira da rajada). */
        MAIN,
        /** As 2 flechas extras da rajada. */
        EXTRA,
        /** Flecha da Chuva de Flechas. */
        RAIN
    }

    /** {@code origin}: olhos do Arqueiro ao atirar. {@code ultra}: o tiro é um Ultra Foco. */
    public static final class Shot {
        private final Kind kind;
        private final Location origin;
        private boolean ultra;
        private SpecialArrow special;
        private int hits;

        Shot(Kind kind, Location origin) {
            this.kind = kind;
            this.origin = origin;
        }

        public Kind kind() {
            return kind;
        }

        public Location origin() {
            return origin;
        }

        public boolean isUltra() {
            return ultra;
        }

        public void setUltra(boolean ultra) {
            this.ultra = ultra;
        }

        /** Tipo da flecha especial deste tiro, ou null (flecha comum). */
        public SpecialArrow special() {
            return special;
        }

        public void setSpecial(SpecialArrow special) {
            this.special = special;
        }

        /** Quantos alvos esta flecha já acertou. */
        public int hits() {
            return hits;
        }

        public void addHit() {
            hits++;
        }
    }

    private final Map<UUID, Shot> shots = new HashMap<>();

    public Shot register(AbstractArrow arrow, Kind kind, Location origin) {
        Shot shot = new Shot(kind, origin.clone());
        shots.put(arrow.getUniqueId(), shot);
        return shot;
    }

    /** A flecha nova continua o tiro da antiga (mesma origem e Ultra Foco), mas como flecha comum. */
    public Shot transfer(AbstractArrow from, AbstractArrow to) {
        Shot old = shots.get(from.getUniqueId());
        if (old == null) {
            return null;
        }
        Shot shot = register(to, old.kind, old.origin);
        shot.ultra = old.ultra;
        return shot;
    }

    public Shot get(AbstractArrow arrow) {
        return shots.get(arrow.getUniqueId());
    }

    public void clear() {
        shots.clear();
    }

    @EventHandler
    public void onRemove(EntityRemoveEvent event) {
        shots.remove(event.getEntity().getUniqueId());
    }
}
