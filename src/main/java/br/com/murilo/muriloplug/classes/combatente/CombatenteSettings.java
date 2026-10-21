package br.com.murilo.muriloplug.classes.combatente;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;

/** Leitura (sempre ao vivo) das opções {@code combatente.*} do config.yml, com os padrões num lugar só. */
public final class CombatenteSettings {

    public static final String EXTRA_HEALTH = "combatente.vida-extra";
    public static final String DAMAGE_REDUCTION = "combatente.reducao-dano";
    public static final String FURY_DURATION = "combatente.furia.duracao-segundos";
    public static final String FURY_COOLDOWN = "combatente.furia.recarga-segundos";
    public static final String FURY_SLOWNESS = "combatente.furia.nivel-lentidao";
    public static final String FURY_SPEED = "combatente.furia.multiplicador-velocidade";
    public static final String BLOCK_CROSSBOW = "combatente.bloquear-besta";

    private static final Map<String, Object> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put(EXTRA_HEALTH, 6);
        DEFAULTS.put(DAMAGE_REDUCTION, 0.3);
        DEFAULTS.put(FURY_DURATION, 8);
        DEFAULTS.put(FURY_COOLDOWN, 30);
        DEFAULTS.put(FURY_SLOWNESS, 1);
        DEFAULTS.put(FURY_SPEED, 1.5);
        DEFAULTS.put(BLOCK_CROSSBOW, false);
    }

    private final JavaPlugin plugin;

    public CombatenteSettings(JavaPlugin plugin) {
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

    public double extraHealth() {
        return Math.max(0, number(EXTRA_HEALTH));
    }

    /** Fração de dano removida (0.3 = 30% menos), limitada a 0..1. */
    public double damageReduction() {
        return Math.max(0, Math.min(1, number(DAMAGE_REDUCTION)));
    }

    public int furyDurationSeconds() {
        return Math.max(1, (int) Math.round(number(FURY_DURATION)));
    }

    public int furyCooldownSeconds() {
        return Math.max(0, (int) Math.round(number(FURY_COOLDOWN)));
    }

    /** Nível da Lentidão (1 = Lentidão I); 0 desliga o efeito. */
    public int furySlownessLevel() {
        return Math.max(0, (int) Math.round(number(FURY_SLOWNESS)));
    }

    public double furyAttackSpeed() {
        return Math.max(1, number(FURY_SPEED));
    }

    public boolean blockCrossbow() {
        return plugin.getConfig().getBoolean(BLOCK_CROSSBOW, false);
    }
}
