package br.com.murilo.muriloplug.classes.mago;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;

/** Leitura (sempre ao vivo) das opções {@code mago.*} do config.yml, com os padrões num lugar só. */
public final class MagoSettings {

    public static final String TEXTURE = "mago.textura-personalizada";
    public static final String MANA_MAX = "mago.mana-maxima";
    public static final String MANA_SECONDS = "mago.mana-segundos-por-ponto";
    public static final String EXTRA_DAMAGE = "mago.dano-extra-recebido";

    private static final Map<String, Object> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put(TEXTURE, false);
        DEFAULTS.put(MANA_MAX, 10);
        DEFAULTS.put(MANA_SECONDS, 15);
        DEFAULTS.put(EXTRA_DAMAGE, 1.5);

        staff(StaffType.FOGO, "mana", 2);
        staff(StaffType.FOGO, "recarga", 5);
        staff(StaffType.FOGO, "dano", 4);
        staff(StaffType.FOGO, "raio", 3);
        staff(StaffType.FOGO, "alcance", 30);
        staff(StaffType.FOGO, "fogo-segundos", 3);
        staff(StaffType.FOGO, "cegueira-segundos", 2);

        staff(StaffType.GELO, "mana", 2);
        staff(StaffType.GELO, "recarga", 10);
        staff(StaffType.GELO, "dano", 2);
        staff(StaffType.GELO, "raio", 2);
        staff(StaffType.GELO, "alcance", 30);
        staff(StaffType.GELO, "efeito-segundos", 5);

        staff(StaffType.RAIO, "mana", 3);
        staff(StaffType.RAIO, "recarga", 5);
        staff(StaffType.RAIO, "dano", 6);
        staff(StaffType.RAIO, "alcance", 25);
        staff(StaffType.RAIO, "saltos", 2);
        staff(StaffType.RAIO, "distancia-salto", 5);

        staff(StaffType.TELEPORTE, "mana", 5);
        staff(StaffType.TELEPORTE, "recarga", 30);
        staff(StaffType.TELEPORTE, "alcance", 30);
        staff(StaffType.TELEPORTE, "carga-segundos", 3);
        staff(StaffType.TELEPORTE, "distancia-acompanhantes", 3);
        staff(StaffType.TELEPORTE, "max-acompanhantes", 2);

        staff(StaffType.VENTO, "mana", 2);
        staff(StaffType.VENTO, "recarga", 20);
        staff(StaffType.VENTO, "dano", 3);
        staff(StaffType.VENTO, "raio", 5);
        staff(StaffType.VENTO, "forca-horizontal", 1.5);
        staff(StaffType.VENTO, "forca-vertical", 0.5);
    }

    private final JavaPlugin plugin;

    public MagoSettings(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private static void staff(StaffType type, String key, Object value) {
        DEFAULTS.put(path(type, key), value);
    }

    private static String path(StaffType type, String key) {
        return "mago.staffs." + type.id() + "." + key;
    }

    /** Acrescenta ao config as chaves que faltarem, sem apagar nada. Retorna true se alterou. */
    public static boolean addMissing(FileConfiguration config) {
        boolean changed = false;
        for (Map.Entry<String, Object> entry : DEFAULTS.entrySet()) {
            if (!config.contains(entry.getKey())) {
                config.set(entry.getKey(), entry.getValue());
                changed = true;
            }
        }
        return changed;
    }

    public double number(String path) {
        Object fallback = DEFAULTS.get(path);
        return plugin.getConfig().getDouble(path, fallback instanceof Number n ? n.doubleValue() : 0);
    }

    public double staff(StaffType type, String key) {
        return number(path(type, key));
    }

    public int staffInt(StaffType type, String key) {
        return (int) Math.round(staff(type, key));
    }

    public boolean customTexture() {
        return plugin.getConfig().getBoolean(TEXTURE, false);
    }

    public int maxMana() {
        return Math.max(1, (int) Math.round(number(MANA_MAX)));
    }

    public double extraDamage() {
        return number(EXTRA_DAMAGE);
    }

    public int manaCost(StaffType type) {
        return Math.max(0, staffInt(type, "mana"));
    }

    public double cooldownSeconds(StaffType type) {
        return Math.max(0, staff(type, "recarga"));
    }

    /** Número sem ".0" quando é inteiro (para a lore). */
    public static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
