package br.com.murilo.muriloplug.classes.combatente;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Leitura (sempre ao vivo) das opções {@code combatente.*} do config.yml, com os padrões num lugar só. */
public final class CombatenteSettings {

    private static final Map<String, Object> DEFAULTS = new LinkedHashMap<>();
    /** Opções do Grito de Fúria antigo e da besta, que deixaram de existir. */
    private static final List<String> OBSOLETE = List.of(
            "combatente.furia.duracao-segundos", "combatente.furia.recarga-segundos",
            "combatente.furia.nivel-lentidao", "combatente.furia.multiplicador-velocidade",
            "combatente.bloquear-besta");

    static {
        DEFAULTS.put("combatente.vida-extra", 6);
        DEFAULTS.put("combatente.reducao-dano", 0.2);
        DEFAULTS.put("combatente.reducao-queda", 0.2);
        DEFAULTS.put("combatente.dano-extra-magia", 0.3);
        DEFAULTS.put("combatente.furia.maxima", 100);
        DEFAULTS.put("combatente.furia.por-golpe", 5);
        DEFAULTS.put("combatente.furia.por-dano-recebido", 2);
        DEFAULTS.put("combatente.furia.queda-fora-combate", 2);
        DEFAULTS.put("combatente.furia.fora-combate-segundos", 5);
        DEFAULTS.put("combatente.berserker.furia-minima", 60);
        DEFAULTS.put("combatente.berserker.consumo-por-segundo", 10);
        DEFAULTS.put("combatente.berserker.multiplicador-velocidade", 1.5);
        DEFAULTS.put("combatente.investida.custo", 40);
        DEFAULTS.put("combatente.investida.alcance", 8);
        DEFAULTS.put("combatente.investida.velocidade", 1.3);
        DEFAULTS.put("combatente.investida.empurrao", 0.7);
        DEFAULTS.put("combatente.investida.intervalo-segundos", 1);
        DEFAULTS.put("combatente.investida.protecao-queda-segundos", 3);
        DEFAULTS.put("combatente.golpe-pulando.custo", 15);
        DEFAULTS.put("combatente.golpe-pulando.bonus", 0.2);
        DEFAULTS.put("combatente.golpe-pulando.raio", 3);
        DEFAULTS.put("combatente.golpe-pulando.dano-area", 0.5);
        DEFAULTS.put("combatente.provocar.custo", 20);
        DEFAULTS.put("combatente.provocar.raio", 6);
        DEFAULTS.put("combatente.provocar.bonus", 0.15);
        DEFAULTS.put("combatente.provocar.segundos", 5);
        DEFAULTS.put("combatente.inabalavel.resistencia-empurrao", 0.3);
        DEFAULTS.put("combatente.ultimo-suspiro.furia-minima", 80);
        DEFAULTS.put("combatente.ultimo-suspiro.vida", 6);
        DEFAULTS.put("combatente.ultimo-suspiro.absorcao", 4);
    }

    private final JavaPlugin plugin;

    public CombatenteSettings(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Acrescenta as chaves que faltarem e tira as obsoletas. Retorna true se alterou o config. */
    public static boolean addMissing(FileConfiguration config) {
        boolean changed = false;
        for (Map.Entry<String, Object> entry : DEFAULTS.entrySet()) {
            if (!config.contains(entry.getKey())) {
                config.set(entry.getKey(), entry.getValue());
                changed = true;
            }
        }
        for (String path : OBSOLETE) {
            if (config.contains(path)) {
                config.set(path, null);
                changed = true;
            }
        }
        return changed;
    }

    private double number(String path) {
        Object fallback = DEFAULTS.get(path);
        return plugin.getConfig().getDouble(path, fallback instanceof Number n ? n.doubleValue() : 0);
    }

    private static double fraction(double value) {
        return Math.max(0, Math.min(1, value));
    }

    public double extraHealth() {
        return Math.max(0, number("combatente.vida-extra"));
    }

    /** Fração de dano removida das causas comuns (0.2 = 20% menos), de 0 a 1. */
    public double damageReduction() {
        return fraction(number("combatente.reducao-dano"));
    }

    public double fallReduction() {
        return fraction(number("combatente.reducao-queda"));
    }

    /** Fração a mais de dano das magias de clique direito do Mago (0.3 = +30%). */
    public double magicExtraDamage() {
        return Math.max(0, number("combatente.dano-extra-magia"));
    }

    public double furyMax() {
        return Math.max(1, number("combatente.furia.maxima"));
    }

    public double furyPerHit() {
        return Math.max(0, number("combatente.furia.por-golpe"));
    }

    public double furyPerDamageTaken() {
        return Math.max(0, number("combatente.furia.por-dano-recebido"));
    }

    /** Fúria perdida por segundo fora de combate. */
    public double furyDecayPerSecond() {
        return Math.max(0, number("combatente.furia.queda-fora-combate"));
    }

    public long outOfCombatMillis() {
        return (long) (Math.max(0, number("combatente.furia.fora-combate-segundos")) * 1000);
    }

    public double berserkerMinimum() {
        return Math.max(1, number("combatente.berserker.furia-minima"));
    }

    public double berserkerDrainPerSecond() {
        return Math.max(0.1, number("combatente.berserker.consumo-por-segundo"));
    }

    public double berserkerAttackSpeed() {
        return Math.max(1, number("combatente.berserker.multiplicador-velocidade"));
    }

    public double dashCost() {
        return Math.max(0, number("combatente.investida.custo"));
    }

    public double dashRange() {
        return Math.max(1, number("combatente.investida.alcance"));
    }

    /** Blocos por tick durante a investida. */
    public double dashSpeed() {
        return Math.max(0.2, number("combatente.investida.velocidade"));
    }

    public double dashPush() {
        return Math.max(0, number("combatente.investida.empurrao"));
    }

    public long dashIntervalMillis() {
        return (long) (Math.max(0, number("combatente.investida.intervalo-segundos")) * 1000);
    }

    public long dashFallProtectionMillis() {
        return (long) (Math.max(0, number("combatente.investida.protecao-queda-segundos")) * 1000);
    }

    public double leapCost() {
        return Math.max(0, number("combatente.golpe-pulando.custo"));
    }

    public double leapBonus() {
        return Math.max(0, number("combatente.golpe-pulando.bonus"));
    }

    public double leapRadius() {
        return Math.max(0, number("combatente.golpe-pulando.raio"));
    }

    public double leapAreaFraction() {
        return fraction(number("combatente.golpe-pulando.dano-area"));
    }

    public double tauntCost() {
        return Math.max(0, number("combatente.provocar.custo"));
    }

    public double tauntRadius() {
        return Math.max(1, number("combatente.provocar.raio"));
    }

    public double tauntBonus() {
        return Math.max(0, number("combatente.provocar.bonus"));
    }

    public int tauntSeconds() {
        return Math.max(1, (int) Math.round(number("combatente.provocar.segundos")));
    }

    public double knockbackResistance() {
        return fraction(number("combatente.inabalavel.resistencia-empurrao"));
    }

    public double lastBreathMinimum() {
        return Math.max(1, number("combatente.ultimo-suspiro.furia-minima"));
    }

    public double lastBreathHealth() {
        return Math.max(1, number("combatente.ultimo-suspiro.vida"));
    }

    public double lastBreathAbsorption() {
        return Math.max(0, number("combatente.ultimo-suspiro.absorcao"));
    }
}
