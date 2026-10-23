package br.com.murilo.muriloplug.classes.arqueiro;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Leitura (sempre ao vivo) das opções {@code arqueiro.*} do config.yml, com os padrões num lugar só. */
public final class ArqueiroSettings {

    public static final String INCLUDE_CROSSBOW = "arqueiro.incluir-besta";
    public static final String BURST_INTERVAL = "arqueiro.rajada.intervalo-ticks";
    public static final String BURST_COOLDOWN = "arqueiro.rajada.recarga-segundos";
    public static final String BURST_EXTRA_DAMAGE = "arqueiro.rajada.dano-extra";
    public static final String DAMAGE_MULTIPLIER = "arqueiro.flecha-explosiva.multiplicador-dano";
    public static final String EXPLOSION_RADIUS = "arqueiro.flecha-explosiva.raio";
    public static final String EXPLOSION_DAMAGE = "arqueiro.flecha-explosiva.dano-maximo";
    public static final String EXPLOSION_KNOCKBACK = "arqueiro.flecha-explosiva.empurrao";
    public static final String EXPLOSION_COOLDOWN = "arqueiro.flecha-explosiva.recarga-segundos";
    public static final String EXPLOSION_MIN_FORCE = "arqueiro.flecha-explosiva.forca-minima";
    public static final String MELEE_REDUCTION = "arqueiro.reducao-dano-corpo-a-corpo";
    public static final String MELEE_TAKEN = "arqueiro.dano-corpo-a-corpo-recebido";
    public static final String DISTANCE_PER_BLOCK = "arqueiro.distancia.por-bloco";
    public static final String DISTANCE_MAX = "arqueiro.distancia.maximo";
    public static final String HEADSHOT_BONUS = "arqueiro.headshot.bonus";
    public static final String FOCUS_MAX = "arqueiro.foco.maximo";
    public static final String FOCUS_REGEN = "arqueiro.foco.regen-fora-combate";
    public static final String FOCUS_REGEN_SECONDS = "arqueiro.foco.regen-intervalo-segundos";
    public static final String FOCUS_PER_HIT = "arqueiro.foco.por-acerto";
    public static final String JUMP_COST = "arqueiro.pulo-duplo.custo";
    public static final String JUMP_UP = "arqueiro.pulo-duplo.impulso-vertical";
    public static final String JUMP_FORWARD = "arqueiro.pulo-duplo.impulso-frente";
    public static final String DASH_COST = "arqueiro.dash.custo";
    public static final String DASH_DISTANCE = "arqueiro.dash.distancia";
    public static final String DASH_FALL_SECONDS = "arqueiro.dash.protecao-queda-segundos";
    public static final String RAIN_COST = "arqueiro.chuva.custo";
    public static final String RAIN_ARROWS = "arqueiro.chuva.flechas";
    public static final String RAIN_RADIUS = "arqueiro.chuva.raio";
    public static final String RAIN_RANGE = "arqueiro.chuva.alcance";
    public static final String RAIN_DELAY = "arqueiro.chuva.atraso-segundos";
    public static final String MARK_COST = "arqueiro.marca.custo";
    public static final String MARK_SECONDS = "arqueiro.marca.segundos";
    public static final String MARK_BONUS = "arqueiro.marca.bonus";
    public static final String MARK_RANGE = "arqueiro.marca.alcance";
    public static final String ULTRA_COST = "arqueiro.ultra.custo";
    public static final String ULTRA_TICKS = "arqueiro.ultra.ticks-minimos";
    public static final String ULTRA_MULTIPLIER = "arqueiro.ultra.multiplicador";
    public static final String RECOVERY_CHANCE = "arqueiro.recuperacao.chance";
    public static final String FEATHER_HEIGHT = "arqueiro.pes-leves.altura-sem-dano";
    public static final String FEATHER_REDUCTION = "arqueiro.pes-leves.reducao";
    public static final String EAGLE_RANGE = "arqueiro.olhos-de-aguia.alcance";

    public static final String PIERCE_TICKS = "arqueiro.flechas.perfurante.ticks-forca-total";
    public static final String PIERCE_DROP = "arqueiro.flechas.perfurante.queda-por-alvo";
    public static final String PIERCE_MIN = "arqueiro.flechas.perfurante.minimo";
    public static final String PIERCE_ARMOR = "arqueiro.flechas.perfurante.bonus-armadura";
    public static final String PIERCE_WALL = "arqueiro.flechas.perfurante.espessura-max-parede";
    public static final String ICE_LEVEL = "arqueiro.flechas.gelo.nivel";
    public static final String ICE_SECONDS = "arqueiro.flechas.gelo.segundos";
    public static final String SMOKE_RADIUS = "arqueiro.flechas.fumaca.raio";
    public static final String SMOKE_SECONDS = "arqueiro.flechas.fumaca.segundos";
    public static final String CHAIN_SECONDS = "arqueiro.flechas.corrente.segundos";
    public static final String ROPE_COOLDOWN = "arqueiro.flechas.corda.recarga";
    public static final String ROPE_RANGE = "arqueiro.flechas.corda.alcance";
    public static final String ROPE_SPEED = "arqueiro.flechas.corda.velocidade";
    public static final String ROPE_FALL = "arqueiro.flechas.corda.protecao-queda-segundos";

    /** Opções que deixaram de existir (a velocidade extra com o arco foi removida). */
    private static final List<String> OBSOLETE = List.of("arqueiro.bonus-velocidade",
            "arqueiro.flecha-explosiva.forca-explosao", "arqueiro.flecha-explosiva.dano-proprio");
    /** Multiplicador de dano antigo da Flecha Explosiva (3x): quem ainda o tem no config passa para o novo padrão. */
    private static final double OLD_EXPLOSIVE_MULTIPLIER = 3;

    private static final Map<String, Object> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put(INCLUDE_CROSSBOW, false);
        DEFAULTS.put(DISTANCE_PER_BLOCK, 0.01);
        DEFAULTS.put(DISTANCE_MAX, 0.3);
        DEFAULTS.put(HEADSHOT_BONUS, 0.5);
        DEFAULTS.put(BURST_INTERVAL, 4);
        DEFAULTS.put(BURST_COOLDOWN, 10);
        DEFAULTS.put(BURST_EXTRA_DAMAGE, 0.7);
        DEFAULTS.put(DAMAGE_MULTIPLIER, 1.5);
        DEFAULTS.put(EXPLOSION_RADIUS, 3);
        DEFAULTS.put(EXPLOSION_DAMAGE, 6);
        DEFAULTS.put(EXPLOSION_KNOCKBACK, 0.6);
        DEFAULTS.put(EXPLOSION_COOLDOWN, 8);
        DEFAULTS.put(EXPLOSION_MIN_FORCE, 0.8);
        DEFAULTS.put(MELEE_REDUCTION, 0.4);
        DEFAULTS.put(MELEE_TAKEN, 0.15);
        DEFAULTS.put(FOCUS_MAX, 100);
        DEFAULTS.put(FOCUS_REGEN, 5);
        DEFAULTS.put(FOCUS_REGEN_SECONDS, 2);
        DEFAULTS.put(FOCUS_PER_HIT, 2);
        DEFAULTS.put(JUMP_COST, 10);
        DEFAULTS.put(JUMP_UP, 0.8);
        DEFAULTS.put(JUMP_FORWARD, 0.4);
        DEFAULTS.put(DASH_COST, 20);
        DEFAULTS.put(DASH_DISTANCE, 5);
        DEFAULTS.put(DASH_FALL_SECONDS, 2);
        DEFAULTS.put(RAIN_COST, 40);
        DEFAULTS.put(RAIN_ARROWS, 10);
        DEFAULTS.put(RAIN_RADIUS, 4);
        DEFAULTS.put(RAIN_RANGE, 40);
        DEFAULTS.put(RAIN_DELAY, 1);
        DEFAULTS.put(MARK_COST, 60);
        DEFAULTS.put(MARK_SECONDS, 10);
        DEFAULTS.put(MARK_BONUS, 0.15);
        DEFAULTS.put(MARK_RANGE, 40);
        DEFAULTS.put(ULTRA_COST, 100);
        DEFAULTS.put(ULTRA_TICKS, 40);
        DEFAULTS.put(ULTRA_MULTIPLIER, 2.5);
        DEFAULTS.put(RECOVERY_CHANCE, 0.3);
        DEFAULTS.put(FEATHER_HEIGHT, 6);
        DEFAULTS.put(FEATHER_REDUCTION, 0.5);
        DEFAULTS.put(EAGLE_RANGE, 20);
        DEFAULTS.put(PIERCE_TICKS, 28);
        DEFAULTS.put(PIERCE_DROP, 0.3);
        DEFAULTS.put(PIERCE_MIN, 0.1);
        DEFAULTS.put(PIERCE_ARMOR, 0.2);
        DEFAULTS.put(PIERCE_WALL, 1);
        DEFAULTS.put(ICE_LEVEL, 2);
        DEFAULTS.put(ICE_SECONDS, 3);
        DEFAULTS.put(SMOKE_RADIUS, 4);
        DEFAULTS.put(SMOKE_SECONDS, 5);
        DEFAULTS.put(CHAIN_SECONDS, 1);
        DEFAULTS.put(ROPE_COOLDOWN, 3);
        DEFAULTS.put(ROPE_RANGE, 30);
        DEFAULTS.put(ROPE_SPEED, 1.4);
        DEFAULTS.put(ROPE_FALL, 3);
    }

    private final JavaPlugin plugin;

    public ArqueiroSettings(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Acrescenta ao config as chaves que faltarem e tira as obsoletas. Retorna true se alterou. */
    public static boolean addMissing(FileConfiguration config) {
        boolean changed = false;
        for (String path : OBSOLETE) {
            if (config.contains(path)) {
                config.set(path, null);
                changed = true;
            }
        }
        if (config.contains(DAMAGE_MULTIPLIER) && config.getDouble(DAMAGE_MULTIPLIER) == OLD_EXPLOSIVE_MULTIPLIER) {
            config.set(DAMAGE_MULTIPLIER, DEFAULTS.get(DAMAGE_MULTIPLIER));
            changed = true;
        }
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

    private int whole(String path) {
        return (int) Math.round(number(path));
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

    // ------------------------------------------------------------ rajada e flecha explosiva

    public int burstIntervalTicks() {
        return Math.max(1, whole(BURST_INTERVAL));
    }

    public double burstCooldownSeconds() {
        return Math.max(0, number(BURST_COOLDOWN));
    }

    /** Fração do dano que as flechas extras da rajada dão (0.7 = 70%). */
    public double burstExtraDamage() {
        return Math.max(0, number(BURST_EXTRA_DAMAGE));
    }

    public double explosiveDamageMultiplier() {
        return Math.max(0, number(DAMAGE_MULTIPLIER));
    }

    public double explosionRadius() {
        return Math.max(0.5, number(EXPLOSION_RADIUS));
    }

    /** Dano da explosão no centro; cai em linha reta até 0 na borda do raio. */
    public double explosionDamage() {
        return Math.max(0, number(EXPLOSION_DAMAGE));
    }

    public double explosionKnockback() {
        return Math.max(0, number(EXPLOSION_KNOCKBACK));
    }

    public double explosiveCooldownSeconds() {
        return Math.max(0, number(EXPLOSION_COOLDOWN));
    }

    /** Força mínima do arco (0 a 1) para a Flecha Explosiva sair explosiva. */
    public double explosiveMinForce() {
        return Math.max(0, Math.min(1, number(EXPLOSION_MIN_FORCE)));
    }

    // ------------------------------------------------------------ dano

    /** Fração de dano removida dos golpes de espada/machado (0.4 = 40% menos), limitada a 0..1. */
    public double meleeReduction() {
        return Math.max(0, Math.min(1, number(MELEE_REDUCTION)));
    }

    /** Dano a mais de golpes corpo a corpo recebidos (0.15 = +15%). */
    public double meleeTakenBonus() {
        return Math.max(0, number(MELEE_TAKEN));
    }

    public double distancePerBlock() {
        return Math.max(0, number(DISTANCE_PER_BLOCK));
    }

    public double distanceMax() {
        return Math.max(0, number(DISTANCE_MAX));
    }

    public double headshotBonus() {
        return Math.max(0, number(HEADSHOT_BONUS));
    }

    // ------------------------------------------------------------ Foco

    public int focusMax() {
        return Math.max(1, whole(FOCUS_MAX));
    }

    public int focusRegen() {
        return Math.max(0, whole(FOCUS_REGEN));
    }

    public int focusRegenIntervalTicks() {
        return Math.max(1, (int) Math.round(number(FOCUS_REGEN_SECONDS) * 20));
    }

    public int focusPerHit() {
        return Math.max(0, whole(FOCUS_PER_HIT));
    }

    public int jumpCost() {
        return Math.max(0, whole(JUMP_COST));
    }

    public double jumpUp() {
        return number(JUMP_UP);
    }

    public double jumpForward() {
        return number(JUMP_FORWARD);
    }

    public int dashCost() {
        return Math.max(0, whole(DASH_COST));
    }

    public double dashDistance() {
        return Math.max(1, number(DASH_DISTANCE));
    }

    public long dashFallProtectionMillis() {
        return (long) (Math.max(0, number(DASH_FALL_SECONDS)) * 1000);
    }

    public int rainCost() {
        return Math.max(0, whole(RAIN_COST));
    }

    public int rainArrows() {
        return Math.max(1, whole(RAIN_ARROWS));
    }

    public double rainRadius() {
        return Math.max(0.5, number(RAIN_RADIUS));
    }

    public double rainRange() {
        return Math.max(1, number(RAIN_RANGE));
    }

    public int rainDelayTicks() {
        return Math.max(0, (int) Math.round(number(RAIN_DELAY) * 20));
    }

    public int markCost() {
        return Math.max(0, whole(MARK_COST));
    }

    public int markSeconds() {
        return Math.max(1, whole(MARK_SECONDS));
    }

    public double markBonus() {
        return Math.max(0, number(MARK_BONUS));
    }

    public double markRange() {
        return Math.max(1, number(MARK_RANGE));
    }

    public int ultraCost() {
        return Math.max(0, whole(ULTRA_COST));
    }

    public int ultraMinTicks() {
        return Math.max(1, whole(ULTRA_TICKS));
    }

    public double ultraMultiplier() {
        return Math.max(0, number(ULTRA_MULTIPLIER));
    }

    public double recoveryChance() {
        return Math.max(0, Math.min(1, number(RECOVERY_CHANCE)));
    }

    public double featherSafeHeight() {
        return Math.max(0, number(FEATHER_HEIGHT));
    }

    public double featherReduction() {
        return Math.max(0, Math.min(1, number(FEATHER_REDUCTION)));
    }

    public double eagleRange() {
        return Math.max(1, number(EAGLE_RANGE));
    }

    // ------------------------------------------------------------ flechas especiais

    /** Ticks puxando o arco para a Flecha Perfurante sair com força total. */
    public int pierceFullTicks() {
        return Math.max(1, whole(PIERCE_TICKS));
    }

    /** Fração de dano perdida a cada alvo atravessado (0.3: 100%, 70%, 49%...). */
    public double pierceDrop() {
        return Math.max(0, Math.min(0.99, number(PIERCE_DROP)));
    }

    /** A flecha para quando o próximo dano ficaria abaixo desta fração do original. */
    public double pierceMinimum() {
        return Math.max(0.001, Math.min(1, number(PIERCE_MIN)));
    }

    /** Quantos alvos a Perfurante pode atravessar (o dano de cada um cai por {@link #pierceDrop()}). */
    public int pierceMaxTargets() {
        double drop = pierceDrop();
        double fraction = 1;
        int targets = 1;
        while (targets < 20 && fraction * (1 - drop) >= pierceMinimum()) {
            fraction *= 1 - drop;
            targets++;
        }
        return targets;
    }

    public double pierceArmorBonus() {
        return Math.max(0, number(PIERCE_ARMOR));
    }

    public int pierceWallThickness() {
        return Math.max(0, whole(PIERCE_WALL));
    }

    /** Nível da Lentidão (2 = Lentidão II). */
    public int iceLevel() {
        return Math.max(1, whole(ICE_LEVEL));
    }

    public double iceSeconds() {
        return Math.max(0, number(ICE_SECONDS));
    }

    public double smokeRadius() {
        return Math.max(0.5, number(SMOKE_RADIUS));
    }

    public int smokeSeconds() {
        return Math.max(1, whole(SMOKE_SECONDS));
    }

    public double chainSeconds() {
        return Math.max(0, number(CHAIN_SECONDS));
    }

    public double ropeCooldownSeconds() {
        return Math.max(0, number(ROPE_COOLDOWN));
    }

    public double ropeRange() {
        return Math.max(1, number(ROPE_RANGE));
    }

    public double ropeSpeed() {
        return Math.max(0.1, number(ROPE_SPEED));
    }

    public int ropeFallProtectionSeconds() {
        return Math.max(0, whole(ROPE_FALL));
    }
}
