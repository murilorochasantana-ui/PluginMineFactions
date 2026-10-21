package br.com.murilo.muriloplug.classes.suporte;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;

/** Leitura (sempre ao vivo) das opções {@code suporte.*} do config.yml, com os padrões num lugar só. */
public final class SuporteSettings {

    public static final String REDUCED_HEALTH = "suporte.vida-reduzida";
    public static final String LINK_PERCENT = "suporte.vinculo.porcentagem";
    public static final String LINK_STOP_HEALTH = "suporte.vinculo.parar-com-vida";
    public static final String LINK_MAX_DISTANCE = "suporte.vinculo.distancia-maxima";
    public static final String LINK_RANGE = "suporte.vinculo.alcance";
    public static final String ROOT_SECONDS = "suporte.prender.segundos";
    public static final String ROOT_RANGE = "suporte.prender.alcance";
    public static final String ROOT_COOLDOWN = "suporte.prender.recarga";
    public static final String ROOT_USES = "suporte.prender.usos";
    public static final String HEAL_AMOUNT = "suporte.cura.quantidade";
    public static final String HEAL_RADIUS = "suporte.cura.raio";
    public static final String HEAL_COOLDOWN = "suporte.cura.recarga";
    public static final String HEAL_USES = "suporte.cura.usos";
    public static final String SCROLL_LEVEL = "suporte.pergaminho.nivel";
    public static final String SCROLL_SECONDS = "suporte.pergaminho.segundos";
    public static final String SCROLL_RADIUS = "suporte.pergaminho.raio";
    public static final String SCROLL_COOLDOWN = "suporte.pergaminho.recarga";
    public static final String FLIGHT_SECONDS = "suporte.voo.segundos";
    public static final String FLIGHT_COOLDOWN = "suporte.voo.recarga";
    public static final String FALL_PROTECTION = "suporte.voo.protecao-queda-segundos";

    private static final Map<String, Object> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put(REDUCED_HEALTH, 4);
        DEFAULTS.put(LINK_PERCENT, 0.35);
        DEFAULTS.put(LINK_STOP_HEALTH, 4);
        DEFAULTS.put(LINK_MAX_DISTANCE, 20);
        DEFAULTS.put(LINK_RANGE, 10);
        DEFAULTS.put(ROOT_SECONDS, 1.5);
        DEFAULTS.put(ROOT_RANGE, 20);
        DEFAULTS.put(ROOT_COOLDOWN, 8);
        DEFAULTS.put(ROOT_USES, 25);
        DEFAULTS.put(HEAL_AMOUNT, 6);
        DEFAULTS.put(HEAL_RADIUS, 6);
        DEFAULTS.put(HEAL_COOLDOWN, 8);
        DEFAULTS.put(HEAL_USES, 8);
        DEFAULTS.put(SCROLL_LEVEL, 1);
        DEFAULTS.put(SCROLL_SECONDS, 8);
        DEFAULTS.put(SCROLL_RADIUS, 8);
        DEFAULTS.put(SCROLL_COOLDOWN, 20);
        DEFAULTS.put(FLIGHT_SECONDS, 5);
        DEFAULTS.put(FLIGHT_COOLDOWN, 15);
        DEFAULTS.put(FALL_PROTECTION, 10);
    }

    private final JavaPlugin plugin;

    public SuporteSettings(JavaPlugin plugin) {
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

    /** Vida máxima a menos do Suporte (4 = 2 corações). */
    public double reducedHealth() {
        return Math.max(0, number(REDUCED_HEALTH));
    }

    /** Fração do dano passada ao Suporte (0.35 = 35%), limitada a 0..1. */
    public double linkPercent() {
        return Math.max(0, Math.min(1, number(LINK_PERCENT)));
    }

    /** Com a vida do Suporte nisto ou abaixo, o vínculo para de dividir dano. */
    public double linkStopHealth() {
        return Math.max(0, number(LINK_STOP_HEALTH));
    }

    public double linkMaxDistance() {
        return Math.max(1, number(LINK_MAX_DISTANCE));
    }

    public double linkRange() {
        return Math.max(1, number(LINK_RANGE));
    }

    public double rootSeconds() {
        return Math.max(0.1, number(ROOT_SECONDS));
    }

    public double rootRange() {
        return Math.max(1, number(ROOT_RANGE));
    }

    public double healAmount() {
        return Math.max(0, number(HEAL_AMOUNT));
    }

    public double healRadius() {
        return Math.max(1, number(HEAL_RADIUS));
    }

    /** Nível da Velocidade do Pergaminho (1 = Velocidade I). */
    public int scrollLevel() {
        return Math.max(1, (int) Math.round(number(SCROLL_LEVEL)));
    }

    public int scrollSeconds() {
        return Math.max(1, (int) Math.round(number(SCROLL_SECONDS)));
    }

    public double scrollRadius() {
        return Math.max(1, number(SCROLL_RADIUS));
    }

    public int flightSeconds() {
        return Math.max(1, (int) Math.round(number(FLIGHT_SECONDS)));
    }

    public int fallProtectionSeconds() {
        return Math.max(1, (int) Math.round(number(FALL_PROTECTION)));
    }

    /** Recarga do item em segundos (0 = sem recarga). */
    public double cooldownSeconds(SuporteItem item) {
        return Math.max(0, switch (item) {
            case VINCULO -> 0;
            case PRENDER -> number(ROOT_COOLDOWN);
            case CURA -> number(HEAL_COOLDOWN);
            case PERGAMINHO -> number(SCROLL_COOLDOWN);
            case VOO -> number(FLIGHT_COOLDOWN);
        });
    }

    /** Usos por item (0 = ilimitado). */
    public int uses(SuporteItem item) {
        return Math.max(0, (int) Math.round(switch (item) {
            case PRENDER -> number(ROOT_USES);
            case CURA -> number(HEAL_USES);
            default -> 0;
        }));
    }
}
