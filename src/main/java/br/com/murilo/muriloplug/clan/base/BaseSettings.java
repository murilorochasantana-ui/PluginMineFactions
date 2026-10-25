package br.com.murilo.muriloplug.clan.base;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Números do Núcleo, do Cofre, do Poder e da manutenção dos claims (config: clan.nucleo/cofre/poder/manutencao). */
public final class BaseSettings {

    public static final String CORE_HP = "clan.nucleo.vida";
    public static final String CORE_RADIUS = "clan.nucleo.raio-explosao";
    public static final String CORE_VULNERABLE = "clan.nucleo.vulneravel-minutos";
    public static final String CORE_REWARD = "clan.nucleo.recompensa";
    public static final String CORE_REPLACE_WAIT = "clan.nucleo.espera-recolocar-horas";
    public static final String CORE_REGEN = "clan.nucleo.regenera-minutos";
    public static final String CORE_PICKUP_WAIT = "clan.nucleo.recolher-sem-ataque-minutos";
    public static final String VAULT_HP = "clan.cofre.vida";
    public static final String VAULT_DISTANCE = "clan.cofre.distancia-maxima";
    public static final String VAULT_INTEREST = "clan.cofre.juros-dia";
    public static final String VAULT_INTEREST_CAP = "clan.cofre.teto-juros";
    public static final String VAULT_LOSS = "clan.cofre.perda";
    public static final String VAULT_LOSS_VULNERABLE = "clan.cofre.perda-vulneravel";
    public static final String UPKEEP_TIME = "clan.manutencao.horario";
    public static final String UPKEEP_PER_BLOCK = "clan.manutencao.por-bloco";
    public static final String UPKEEP_POWER_WEIGHT = "clan.manutencao.peso-poder";
    public static final String POWER_KILL = "clan.poder.kill";
    public static final String POWER_CORE_DESTROYED = "clan.poder.nucleo-destruido";
    public static final String POWER_RAID_DEFENDED = "clan.poder.raid-defendido";
    public static final String POWER_CORE_LOST = "clan.poder.nucleo-perdido";

    private static final Map<String, Object> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put(CORE_HP, 10);
        DEFAULTS.put(CORE_RADIUS, 4);
        DEFAULTS.put(CORE_VULNERABLE, 30);
        DEFAULTS.put(CORE_REWARD, 5000);
        DEFAULTS.put(CORE_REPLACE_WAIT, 2);
        DEFAULTS.put(CORE_REGEN, 10);
        DEFAULTS.put(CORE_PICKUP_WAIT, 10);
        DEFAULTS.put(VAULT_HP, 6);
        DEFAULTS.put(VAULT_DISTANCE, 10);
        DEFAULTS.put(VAULT_INTEREST, 0.02);
        DEFAULTS.put(VAULT_INTEREST_CAP, 100000);
        DEFAULTS.put(VAULT_LOSS, 0.3);
        DEFAULTS.put(VAULT_LOSS_VULNERABLE, 0.5);
        DEFAULTS.put(UPKEEP_TIME, "20:00");
        DEFAULTS.put(UPKEEP_PER_BLOCK, 0.5);
        DEFAULTS.put(UPKEEP_POWER_WEIGHT, 0.01);
        DEFAULTS.put(POWER_KILL, 1);
        DEFAULTS.put(POWER_CORE_DESTROYED, 10);
        DEFAULTS.put(POWER_RAID_DEFENDED, 2);
        DEFAULTS.put(POWER_CORE_LOST, 5);
    }

    private final JavaPlugin plugin;

    public BaseSettings(JavaPlugin plugin) {
        this.plugin = plugin;
    }

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
        return plugin.getConfig().getDouble(path, ((Number) DEFAULTS.get(path)).doubleValue());
    }

    private int integer(String path) {
        return (int) Math.round(number(path));
    }

    public int coreHp() {
        return Math.max(1, integer(CORE_HP));
    }

    public double coreRadius() {
        return Math.max(0.5, number(CORE_RADIUS));
    }

    public long vulnerableMillis() {
        return Math.max(0, integer(CORE_VULNERABLE)) * 60_000L;
    }

    public long coreReward() {
        return Math.max(0, Math.round(number(CORE_REWARD)));
    }

    public long replaceWaitMillis() {
        return Math.max(0, (long) (number(CORE_REPLACE_WAIT) * 3_600_000L));
    }

    public long regenMillis() {
        return Math.max(1, integer(CORE_REGEN)) * 60_000L;
    }

    public long pickupWaitMillis() {
        return Math.max(0, integer(CORE_PICKUP_WAIT)) * 60_000L;
    }

    public int vaultHp() {
        return Math.max(1, integer(VAULT_HP));
    }

    public double vaultDistance() {
        return Math.max(1, number(VAULT_DISTANCE));
    }

    public double interestPerDay() {
        return Math.max(0, number(VAULT_INTEREST));
    }

    public long interestCap() {
        return Math.max(0, Math.round(number(VAULT_INTEREST_CAP)));
    }

    public double vaultLoss(boolean vulnerable) {
        return Math.max(0, Math.min(1, number(vulnerable ? VAULT_LOSS_VULNERABLE : VAULT_LOSS)));
    }

    /** Horário da manutenção diária (padrão 20:00 se o config estiver errado). */
    public LocalTime upkeepTime() {
        try {
            return LocalTime.parse(plugin.getConfig().getString(UPKEEP_TIME, "20:00").trim());
        } catch (DateTimeParseException e) {
            return LocalTime.of(20, 0);
        }
    }

    public double upkeepPerBlock() {
        return Math.max(0, number(UPKEEP_PER_BLOCK));
    }

    public double upkeepPowerWeight() {
        return Math.max(0, number(UPKEEP_POWER_WEIGHT));
    }

    public int powerKill() {
        return integer(POWER_KILL);
    }

    public int powerCoreDestroyed() {
        return integer(POWER_CORE_DESTROYED);
    }

    public int powerRaidDefended() {
        return integer(POWER_RAID_DEFENDED);
    }

    public int powerCoreLost() {
        return integer(POWER_CORE_LOST);
    }
}
