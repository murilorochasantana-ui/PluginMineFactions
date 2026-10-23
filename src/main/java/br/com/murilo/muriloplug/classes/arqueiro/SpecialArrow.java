package br.com.murilo.muriloplug.classes.arqueiro;

import java.util.Locale;

/** Tipos de flecha especial do Arqueiro. O id é o valor guardado em mineplug:flecha (PersistentDataContainer). */
public enum SpecialArrow {
    EXPLOSIVA("explosiva", "§cFlecha Explosiva"),
    PERFURANTE("perfurante", "§5Flecha Perfurante"),
    GELO("gelo", "§bFlecha de Gelo"),
    FUMACA("fumaca", "§8Flecha de Fumaça"),
    CORRENTE("corrente", "§7Flecha de Corrente"),
    CORDA("corda", "§6Flecha de Corda");

    private final String id;
    private final String displayName;

    SpecialArrow(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** O tipo com este id (sem diferenciar maiúsculas), ou null. */
    public static SpecialArrow byId(String id) {
        if (id != null) {
            for (SpecialArrow type : values()) {
                if (type.id.equals(id.toLowerCase(Locale.ROOT))) {
                    return type;
                }
            }
        }
        return null;
    }
}
