package br.com.murilo.muriloplug.classes.arqueiro;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;

/** Leitura (sempre ao vivo) das opções {@code arqueiro.*} do config.yml, com os padrões num lugar só. */
public final class ArqueiroSettings {

    public static final String INCLUDE_CROSSBOW = "arqueiro.incluir-besta";
    public static final String SPEED_BONUS = "arqueiro.bonus-velocidade";
    public static final String BURST_INTERVAL = "arqueiro.rajada.intervalo-ticks";
    public static final String BURST_COOLDOWN = "arqueiro.rajada.recarga-segundos";
    public static final String DAMAGE_MULTIPLIER = "arqueiro.flecha-explosiva.multiplicador-dano";
    public static final String EXPLOSION_POWER = "arqueiro.flecha-explosiva.forca-explosao";
    public static final String SELF_DAMAGE = "arqueiro.flecha-explosiva.dano-proprio";
    public static final String MELEE_REDUCTION = "arqueiro.reducao-dano-corpo-a-corpo";

    private static final Map<String, Object> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put(INCLUDE_CROSSBOW, false);
        DEFAULTS.put(SPEED_BONUS, 0.2);
        DEFAULTS.put(BURST_INTERVAL, 4);
        DEFAULTS.put(BURST_COOLDOWN, 3);
        DEFAULTS.put(DAMAGE_MULTIPLIER, 3);
        DEFAULTS.put(EXPLOSION_POWER, 3.0);
        DEFAULTS.put(SELF_DAMAGE, false);
        DEFAULTS.put(MELEE_REDUCTION, 0.4);
    }

    private final JavaPlugin plugin;

    public ArqueiroSettings(JavaPlugin plugin) {
        this.plugin = plugin;
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

    private double number(String path) {
        Object fallback = DEFAULTS.get(path);
        return plugin.getConfig().getDouble(path, fallback instanceof Number n ? n.doubleValue() : 0);
    }

    private boolean flag(String path) {
        return plugin.getConfig().getBoolean(path, (Boolean) DEFAULTS.get(path));
    }

    /** O item conta como "arco" do Arqueiro (arco; besta só se {@code arqueiro.incluir-besta} estiver ligado). */
    public boolean isArcherBow(ItemStack item) {
        if (item == null) {
            return false;
        }
        Material type = item.getType();
        return type == Material.BOW || (type == Material.CROSSBOW && flag(INCLUDE_CROSSBOW));
    }

    public double speedBonus() {
        return Math.max(0, number(SPEED_BONUS));
    }

    public int burstIntervalTicks() {
        return Math.max(1, (int) Math.round(number(BURST_INTERVAL)));
    }

    public double burstCooldownSeconds() {
        return Math.max(0, number(BURST_COOLDOWN));
    }

    public double explosiveDamageMultiplier() {
        return Math.max(0, number(DAMAGE_MULTIPLIER));
    }

    public float explosionPower() {
        return (float) Math.max(0, number(EXPLOSION_POWER));
    }

    public boolean selfDamage() {
        return flag(SELF_DAMAGE);
    }

    /** Fração de dano removida dos golpes de espada/machado (0.4 = 40% menos), limitada a 0..1. */
    public double meleeReduction() {
        return Math.max(0, Math.min(1, number(MELEE_REDUCTION)));
    }
}
