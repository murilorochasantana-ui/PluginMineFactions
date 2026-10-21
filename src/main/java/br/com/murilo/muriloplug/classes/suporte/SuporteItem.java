package br.com.murilo.muriloplug.classes.suporte;

import org.bukkit.Material;

/** Os 5 itens do Suporte. O id é o valor guardado no PersistentDataContainer (chave mineplug:suporte_item). */
public enum SuporteItem {

    VINCULO("vinculo", Material.HEAVY_CORE, "§5Núcleo do Vínculo"),
    PRENDER("prender", Material.NETHER_STAR, "§8Estrela das Correntes"),
    CURA("cura", Material.HEART_OF_THE_SEA, "§bCoração da Cura"),
    PERGAMINHO("pergaminho", Material.WRITABLE_BOOK, "§ePergaminho da Velocidade"),
    VOO("voo", Material.FEATHER, "§fPena do Voo");

    private final String id;
    private final Material material;
    private final String displayName;

    SuporteItem(String id, Material material, String displayName) {
        this.id = id;
        this.material = material;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public Material material() {
        return material;
    }

    /** Nome com cor, como aparece no item. */
    public String displayName() {
        return displayName;
    }

    public static SuporteItem byId(String id) {
        if (id == null) {
            return null;
        }
        for (SuporteItem item : values()) {
            if (item.id.equals(id)) {
                return item;
            }
        }
        return null;
    }
}
