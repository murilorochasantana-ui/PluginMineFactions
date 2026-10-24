package br.com.murilo.muriloplug.teste;

import br.com.murilo.muriloplug.clan.ClanManager;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.UUID;

/**
 * Quem é inimigo e quem é aliado de quem, num lugar só: as habilidades de todas as classes usam isto em vez de olhar
 * "Player" e "mesmo clan" por conta própria. Jogador: aliado = mesmo clan, inimigo = não ser do clan. Boneco de teste
 * inimigo é inimigo de todos e o aliado é aliado de todos. Mobs são inimigos. O Totem de Cura do Suporte é inimigo
 * de quem não é do clan do dono e nunca é aliado (ninguém o cura nem o fortalece). (Estar logado, vivo ou no
 * espectador é conferido por quem escolhe o alvo.)
 */
public final class TargetRelations {

    /** Tipo do boneco de teste, guardado no PersistentDataContainer da entidade. */
    public enum DummyType {
        INIMIGO("inimigo"), ALIADO("aliado");

        private final String id;

        DummyType(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static DummyType byId(String id) {
            if (id != null) {
                for (DummyType type : values()) {
                    if (type.id.equals(id.toLowerCase(Locale.ROOT))) {
                        return type;
                    }
                }
            }
            return null;
        }
    }

    private final ClanManager clans;
    private final NamespacedKey dummyKey;
    private final NamespacedKey totemKey;

    public TargetRelations(JavaPlugin plugin, ClanManager clans) {
        this.clans = clans;
        this.dummyKey = new NamespacedKey(plugin, "boneco");
        this.totemKey = new NamespacedKey(plugin, "suporte_totem");
    }

    /** Chave do PersistentDataContainer que marca as entidades do Totem de Cura (valor: UUID do dono). */
    public NamespacedKey totemKey() {
        return totemKey;
    }

    /** Dono do Totem de Cura, ou null se a entidade não é um totem. */
    public UUID totemOwner(Entity entity) {
        String raw = entity.getPersistentDataContainer().get(totemKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    NamespacedKey dummyKey() {
        return dummyKey;
    }

    /** Tipo do boneco de teste, ou null se a entidade não é um boneco. */
    public DummyType dummyType(Entity entity) {
        return DummyType.byId(entity.getPersistentDataContainer().get(dummyKey, PersistentDataType.STRING));
    }

    public boolean isDummy(Entity entity) {
        return dummyType(entity) != null;
    }

    public boolean isEnemy(Player origin, LivingEntity target) {
        UUID totem = totemOwner(target);
        if (totem != null) {
            return !totem.equals(origin.getUniqueId()) && !clans.isSameClan(origin.getUniqueId(), totem);
        }
        DummyType dummy = dummyType(target);
        if (dummy != null) {
            return dummy == DummyType.INIMIGO;
        }
        if (target instanceof Player player) {
            return !player.equals(origin) && !clans.isSameClan(origin.getUniqueId(), player.getUniqueId());
        }
        return true;
    }

    public boolean isAlly(Player origin, LivingEntity target) {
        if (totemOwner(target) != null) {
            return false;
        }
        DummyType dummy = dummyType(target);
        if (dummy != null) {
            return dummy == DummyType.ALIADO;
        }
        return target instanceof Player player && !player.equals(origin)
                && clans.isSameClan(origin.getUniqueId(), player.getUniqueId());
    }
}
