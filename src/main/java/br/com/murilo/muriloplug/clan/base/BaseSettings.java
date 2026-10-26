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
    public static final String CORE_STEAL_POINT = "clan.nucleo.roubo-por-ponto";
    public static final String CORE_STEAL_DESTROYED = "clan.nucleo.roubo-destruido";
    public static final String VAULT_SHIELD = "clan.cofre.escudo-segundos";
    public static final String VAULT_MIN_BASE = "clan.cofre.minimo-base";
    public static final String VAULT_MIN_POWER = "clan.cofre.minimo-por-poder";
    public static final String CHEST_HP = "clan.bau.vida";
    public static final String CHEST_SHIELD = "clan.bau.escudo-segundos";
    /** Chave antiga (o Núcleo não é mais removido ao ser destruído): apagada do config do servidor ao ligar. */
    public static final String OLD_CORE_REPLACE_WAIT = "clan.nucleo.espera-recolocar-horas";
    public static final String CORE_REACTIVATE_HP = "clan.nucleo.vida-ao-reativar";
    public static final String CORE_REPLACE_DEADLINE = "clan.nucleo.prazo-recolocar-segundos";
    public static final String CORE_REGEN = "clan.nucleo.regenera-minutos";
    public static final String CORE_PICKUP_WAIT = "clan.nucleo.recolher-sem-ataque-minutos";
    public static final String CORE_SHIELD = "clan.nucleo.escudo-segundos";
    public static final String SIZE_MENU = "clan.manutencao.tamanhos-menu";
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
        DEFAULTS.put(CORE_VULNERABLE, 10);
        DEFAULTS.put(CORE_STEAL_POINT, 0.05);
        DEFAULTS.put(CORE_STEAL_DESTROYED, 0.2);
        DEFAULTS.put(VAULT_SHIELD, 3);
        DEFAULTS.put(VAULT_MIN_BASE, 2000);
        DEFAULTS.put(VAULT_MIN_POWER, 100);
        DEFAULTS.put(CHEST_HP, 3);
        DEFAULTS.put(CHEST_SHIELD, 3);
        DEFAULTS.put(CORE_REACTIVATE_HP, 10);
        DEFAULTS.put(CORE_REPLACE_DEADLINE, 60);
        DEFAULTS.put(CORE_REGEN, 10);
        DEFAULTS.put(CORE_PICKUP_WAIT, 10);
        DEFAULTS.put(CORE_SHIELD, 5);
        DEFAULTS.put(SIZE_MENU, java.util.List.of(10, 20, 30, 40, 50));
        DEFAULTS.put(VAULT_HP, 5);
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

    /** Fração do saldo do Cofre roubada a cada ponto novo tirado do Núcleo. */
    public double stealPerPoint() {
        return Math.max(0, Math.min(1, number(CORE_STEAL_POINT)));
    }

    /** Fração do que sobrou no Cofre roubada quando o Núcleo é destruído. */
    public double stealOnDestroy() {
        return Math.max(0, Math.min(1, number(CORE_STEAL_DESTROYED)));
    }

    public long vaultShieldMillis() {
        return Math.max(0, (long) (number(VAULT_SHIELD) * 1000));
    }

    /** Saldo mínimo do Cofre: base + por-poder × Poder. */
    public long vaultMinimum(int power) {
        return Math.max(0, Math.round(number(VAULT_MIN_BASE) + number(VAULT_MIN_POWER) * power));
    }

    public int chestHp() {
        return Math.max(1, integer(CHEST_HP));
    }

    public long chestShieldMillis() {
        return Math.max(0, (long) (number(CHEST_SHIELD) * 1000));
    }

    /** Vida do Núcleo quando ele reativa sozinho (de 1 até a vida máxima). */
    public int coreReactivateHp() {
        return Math.max(1, Math.min(coreHp(), integer(CORE_REACTIVATE_HP)));
    }

    /** Prazo para colocar o Núcleo de novo depois de recolher. */
    public long replaceDeadlineMillis() {
        return Math.max(0, (long) (number(CORE_REPLACE_DEADLINE) * 1000));
    }

    public long regenMillis() {
        return Math.max(1, integer(CORE_REGEN)) * 60_000L;
    }

    public long pickupWaitMillis() {
        return Math.max(0, integer(CORE_PICKUP_WAIT)) * 60_000L;
    }

    /** Imunidade do Núcleo a explosões depois de cada explosão que tira pontos. */
    public long shieldMillis() {
        return Math.max(0, (long) (number(CORE_SHIELD) * 1000));
    }

    /** Tamanhos do menu do /claim (lado do quadrado). */
    public java.util.List<Integer> sizeMenu() {
        java.util.List<Integer> sizes = plugin.getConfig().getIntegerList(SIZE_MENU);
        return sizes.isEmpty() ? java.util.List.of(10, 20, 30, 40, 50) : sizes;
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
