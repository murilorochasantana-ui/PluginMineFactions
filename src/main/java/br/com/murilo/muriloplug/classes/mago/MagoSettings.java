package br.com.murilo.muriloplug.classes.mago;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;

/** Leitura (sempre ao vivo) das opções {@code mago.*} do config.yml, com os padrões num lugar só. */
public final class MagoSettings {

    public static final String TEXTURE = "mago.textura-personalizada";
    public static final String MANA_MAX = "mago.mana-maxima";
    public static final String WEAPON_PENALTY = "mago.penalidade-arma";
    public static final String EXTRA_DAMAGE = "mago.dano-extra-recebido-porcentagem";

    private static final Map<String, Object> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put(TEXTURE, false);
        DEFAULTS.put(MANA_MAX, 10);
        DEFAULTS.put(WEAPON_PENALTY, 0.6);
        DEFAULTS.put(EXTRA_DAMAGE, 0.15);
        DEFAULTS.put("mago.mana.regen-fora-combate", 2);
        DEFAULTS.put("mago.mana.regen-em-combate", 6);
        DEFAULTS.put("mago.mana.por-kill", 3);
        DEFAULTS.put("mago.mana.por-assistencia", 1);
        DEFAULTS.put("mago.missil.intervalo-segundos", 0.6);
        DEFAULTS.put("mago.missil.mana-por-acerto", 1);
        DEFAULTS.put("mago.missil.dano", 2.5);
        DEFAULTS.put("mago.missil.alcance", 30);
        DEFAULTS.put("mago.missil.velocidade", 2);
        DEFAULTS.put("mago.poder-arcano.bonus-por-acumulo", 0.1);
        DEFAULTS.put("mago.poder-arcano.maximo", 3);
        DEFAULTS.put("mago.poder-arcano.segundos", 5);
        DEFAULTS.put("mago.escudo-mana.porcentagem", 0.2);
        DEFAULTS.put("mago.escudo-mana.dano-por-mana", 2);
        DEFAULTS.put("mago.combos.gelo-marca-segundos", 5);
        DEFAULTS.put("mago.combos.vento-marca-segundos", 3);
        DEFAULTS.put("mago.combos.choque-termico.ativado", true);
        DEFAULTS.put("mago.combos.choque-termico.multiplicador-dano", 2);
        DEFAULTS.put("mago.combos.condutor.ativado", true);
        DEFAULTS.put("mago.combos.condutor.saltos", 4);
        DEFAULTS.put("mago.combos.condutor.distancia-salto", 7);
        DEFAULTS.put("mago.combos.tempestade-fogo.ativado", true);
        DEFAULTS.put("mago.combos.tempestade-fogo.fogo-segundos", 6);
        DEFAULTS.put("mago.combos.tempestade-fogo.raio", 4);
        DEFAULTS.put("mago.combos.passo-arcano.ativado", true);
        DEFAULTS.put("mago.combos.passo-arcano.segundos", 4);

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

    /** Opção ligada/desligada (padrão do DEFAULTS, ou ligada se não houver). */
    public boolean flag(String path) {
        Object fallback = DEFAULTS.get(path);
        return plugin.getConfig().getBoolean(path, !(fallback instanceof Boolean b) || b);
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

    /** Fração de dano a mais que o Mago leva de golpes e projéteis (0.15 = +15%). */
    public double extraDamage() {
        return Math.max(0, number(EXTRA_DAMAGE));
    }

    /** Fração de dano perdida com armas (0.6 = 60% menos), entre 0 e 1. */
    public double weaponPenalty() {
        return Math.min(1, Math.max(0, number(WEAPON_PENALTY)));
    }

    /** Segundos para recuperar 1 de mana, fora ou dentro de combate. */
    public double manaRegenSeconds(boolean inCombat) {
        return Math.max(0.5, number(inCombat ? "mago.mana.regen-em-combate" : "mago.mana.regen-fora-combate"));
    }

    public int manaPerKill() {
        return Math.max(0, (int) Math.round(number("mago.mana.por-kill")));
    }

    public int manaPerAssist() {
        return Math.max(0, (int) Math.round(number("mago.mana.por-assistencia")));
    }

    public double missileIntervalSeconds() {
        return Math.max(0, number("mago.missil.intervalo-segundos"));
    }

    public int missileManaPerHit() {
        return Math.max(0, (int) Math.round(number("mago.missil.mana-por-acerto")));
    }

    public double missileDamage() {
        return Math.max(0, number("mago.missil.dano"));
    }

    public double missileRange() {
        return Math.max(1, number("mago.missil.alcance"));
    }

    /** Blocos andados pelo míssil por tick. */
    public double missileSpeed() {
        return Math.max(0.25, number("mago.missil.velocidade"));
    }

    public double arcaneBonus() {
        return Math.max(0, number("mago.poder-arcano.bonus-por-acumulo"));
    }

    public int arcaneMax() {
        return Math.max(0, (int) Math.round(number("mago.poder-arcano.maximo")));
    }

    public double arcaneSeconds() {
        return Math.max(0.5, number("mago.poder-arcano.segundos"));
    }

    /** Fração do dano levado que a mana absorve (0.2 = 20%), entre 0 e 1. */
    public double shieldFraction() {
        return Math.min(1, Math.max(0, number("mago.escudo-mana.porcentagem")));
    }

    /** Pontos de dano absorvidos por 1 de mana. */
    public double shieldDamagePerMana() {
        return Math.max(0.1, number("mago.escudo-mana.dano-por-mana"));
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
