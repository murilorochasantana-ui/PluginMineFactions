package br.com.murilo.muriloplug.classes.engenheiro;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/** Leitura (sempre ao vivo) das opções {@code engenheiro.*} do config.yml, com os padrões num lugar só. */
public final class EngenheiroSettings {

    public static final String AREA_SIZE = "engenheiro.area-tamanho";
    public static final String AREA_HEIGHT = "engenheiro.area-altura";
    public static final String COMBAT_WAIT = "engenheiro.espera-apos-combate-segundos";
    public static final String MAX_MINUTES = "engenheiro.tempo-maximo-minutos";
    public static final String FORBIDDEN = "engenheiro.blocos-proibidos";
    public static final String PLACE_WAIT = "engenheiro.espera-colocar-apos-combate-segundos";
    public static final String TICKS_PER_LAYER = "engenheiro.construcao.ticks-por-camada";
    public static final String ENERGY_PER_SECOND = "engenheiro.energia.por-segundo";
    public static final String ENERGY_MAX = "engenheiro.energia.maximo";
    public static final String EXPLOSION_REDUCTION = "engenheiro.reducao-explosao";
    public static final String REPAIR_COST = "engenheiro.reparo.custo";
    public static final String DAMAGE_SWORD = "engenheiro.dano.espada";
    public static final String DAMAGE_CROSSBOW = "engenheiro.dano.besta";
    public static final String DAMAGE_BOW = "engenheiro.dano.arco";
    public static final String NETHERITE_SLOW = "engenheiro.netherite.lentidao-por-pecas";
    public static final String IRON_CHANCE = "engenheiro.ferro.chance-por-peca";
    public static final String IRON_DAMAGE = "engenheiro.ferro.dano-choque";
    public static final String GRAPPLE_COOLDOWN = "engenheiro.gancho.recarga";
    public static final String GRAPPLE_RANGE = "engenheiro.gancho.distancia-maxima";
    public static final String GRAPPLE_SPEED = "engenheiro.gancho.velocidade";
    public static final String GRAPPLE_FALL = "engenheiro.gancho.protecao-queda-segundos";
    public static final String SENTINEL_MAX = "engenheiro.sentinela.maximo";
    public static final String SENTINEL_RADIUS = "engenheiro.sentinela.raio";
    public static final String SENTINEL_ALERT = "engenheiro.sentinela.intervalo-alerta";
    public static final String PLANTA_MAX = "engenheiro.planta.max-projetos";
    public static final String PLANTA_NAME_MAX = "engenheiro.planta.nome-maximo";

    private static final Map<String, Object> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put(AREA_SIZE, 20);
        DEFAULTS.put(AREA_HEIGHT, 20);
        DEFAULTS.put(COMBAT_WAIT, 60);
        DEFAULTS.put(MAX_MINUTES, 120);
        DEFAULTS.put(PLACE_WAIT, 15);
        DEFAULTS.put(TICKS_PER_LAYER, 10);
        DEFAULTS.put(ENERGY_PER_SECOND, 2.0);
        DEFAULTS.put(ENERGY_MAX, 100);
        DEFAULTS.put(EXPLOSION_REDUCTION, 0.5);
        DEFAULTS.put(REPAIR_COST, 80);
        DEFAULTS.put(DAMAGE_SWORD, -0.3);
        DEFAULTS.put(DAMAGE_CROSSBOW, 0.3);
        DEFAULTS.put(DAMAGE_BOW, -0.2);
        DEFAULTS.put(NETHERITE_SLOW + ".1", 1);
        DEFAULTS.put(NETHERITE_SLOW + ".2", 1);
        DEFAULTS.put(NETHERITE_SLOW + ".3", 2);
        DEFAULTS.put(NETHERITE_SLOW + ".4", 2);
        DEFAULTS.put(IRON_CHANCE, 0.15);
        DEFAULTS.put(IRON_DAMAGE, 6.0);
        DEFAULTS.put(GRAPPLE_COOLDOWN, 3);
        DEFAULTS.put(GRAPPLE_RANGE, 30);
        DEFAULTS.put(GRAPPLE_SPEED, 1.4);
        DEFAULTS.put(GRAPPLE_FALL, 3);
        DEFAULTS.put(SENTINEL_MAX, 3);
        DEFAULTS.put(SENTINEL_RADIUS, 15);
        DEFAULTS.put(SENTINEL_ALERT, 30);
        DEFAULTS.put(PLANTA_MAX, 9);
        DEFAULTS.put(PLANTA_NAME_MAX, 20);
        DEFAULTS.put(FORBIDDEN, List.of("BEDROCK", "SPAWNER", "TRIAL_SPAWNER", "VAULT", "COMMAND_BLOCK",
                "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK", "STRUCTURE_BLOCK", "JIGSAW", "BARRIER", "LIGHT",
                "END_PORTAL_FRAME", "END_PORTAL", "NETHER_PORTAL", "END_GATEWAY", "REINFORCED_DEEPSLATE", "*_BED"));
    }

    private final JavaPlugin plugin;

    public EngenheiroSettings(JavaPlugin plugin) {
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

    public int areaSize() {
        return Math.max(1, plugin.getConfig().getInt(AREA_SIZE, 20));
    }

    public int areaHeight() {
        return Math.max(1, plugin.getConfig().getInt(AREA_HEIGHT, 20));
    }

    public int combatWaitSeconds() {
        return Math.max(0, plugin.getConfig().getInt(COMBAT_WAIT, 60));
    }

    /** Espera depois do combate para COLOCAR uma estrutura (marcar e salvar usam {@link #combatWaitSeconds()}). */
    public int placeWaitSeconds() {
        return Math.max(0, plugin.getConfig().getInt(PLACE_WAIT, 15));
    }

    public int ticksPerLayer() {
        return Math.max(1, plugin.getConfig().getInt(TICKS_PER_LAYER, 10));
    }

    public double energyPerSecond() {
        return Math.max(0, plugin.getConfig().getDouble(ENERGY_PER_SECOND, 2.0));
    }

    public double energyMax() {
        return Math.max(1, plugin.getConfig().getDouble(ENERGY_MAX, 100));
    }

    public double explosionReduction() {
        return Math.max(0, Math.min(1, plugin.getConfig().getDouble(EXPLOSION_REDUCTION, 0.5)));
    }

    public double repairCost() {
        return Math.max(0, plugin.getConfig().getDouble(REPAIR_COST, 80));
    }

    /** Ajuste de dano da espada (-0.3 = 30% menos). */
    public double swordDamage() {
        return plugin.getConfig().getDouble(DAMAGE_SWORD, -0.3);
    }

    /** Ajuste de dano da besta (0.3 = 30% a mais). */
    public double crossbowDamage() {
        return plugin.getConfig().getDouble(DAMAGE_CROSSBOW, 0.3);
    }

    /** Ajuste de dano do arco (-0.2 = 20% menos). */
    public double bowDamage() {
        return plugin.getConfig().getDouble(DAMAGE_BOW, -0.2);
    }

    /** Nível da Lentidão (0 = nenhuma) para esta quantidade de peças de netherite. */
    public int netheriteSlowness(int pieces) {
        int fallback = pieces >= 3 ? 2 : pieces >= 1 ? 1 : 0;
        return Math.max(0, plugin.getConfig().getInt(NETHERITE_SLOW + "." + pieces, fallback));
    }

    public double ironChancePerPiece() {
        return Math.max(0, Math.min(1, plugin.getConfig().getDouble(IRON_CHANCE, 0.15)));
    }

    public double ironShockDamage() {
        return Math.max(0, plugin.getConfig().getDouble(IRON_DAMAGE, 6.0));
    }

    public int grappleCooldownSeconds() {
        return Math.max(0, plugin.getConfig().getInt(GRAPPLE_COOLDOWN, 3));
    }

    public double grappleRange() {
        return Math.max(1, plugin.getConfig().getDouble(GRAPPLE_RANGE, 30));
    }

    public double grappleSpeed() {
        return Math.max(0.1, plugin.getConfig().getDouble(GRAPPLE_SPEED, 1.4));
    }

    public int grappleFallProtectionSeconds() {
        return Math.max(0, plugin.getConfig().getInt(GRAPPLE_FALL, 3));
    }

    public int sentinelMax() {
        return Math.max(1, plugin.getConfig().getInt(SENTINEL_MAX, 3));
    }

    public int sentinelRadius() {
        return Math.max(1, plugin.getConfig().getInt(SENTINEL_RADIUS, 15));
    }

    public int sentinelAlertSeconds() {
        return Math.max(0, plugin.getConfig().getInt(SENTINEL_ALERT, 30));
    }

    /** Máximo de projetos que uma Planta guarda (a lista do menu tem 9 posições). */
    public int plantaMaxProjects() {
        return Math.max(1, Math.min(9, plugin.getConfig().getInt(PLANTA_MAX, 9)));
    }

    public int plantaNameMax() {
        return Math.max(1, plugin.getConfig().getInt(PLANTA_NAME_MAX, 20));
    }

    public int maxMinutes() {
        return Math.max(1, plugin.getConfig().getInt(MAX_MINUTES, 120));
    }

    /**
     * Teste de bloco proibido, montado a partir da lista do config. Uma entrada que começa com "*" vale para todo
     * material que termina com o resto ("*_BED" = todas as camas). Qualquer cama é sempre proibida.
     */
    @SuppressWarnings("unchecked")
    public Predicate<Material> forbidden() {
        List<String> entries = (List<String>) (List<?>) plugin.getConfig().getList(FORBIDDEN, (List<?>) DEFAULTS.get(FORBIDDEN));
        return material -> {
            if (Tag.BEDS.isTagged(material)) {
                return true;
            }
            String name = material.name();
            for (Object raw : entries) {
                String entry = String.valueOf(raw).trim().toUpperCase(Locale.ROOT);
                if (entry.startsWith("*") ? name.endsWith(entry.substring(1)) : name.equals(entry)) {
                    return true;
                }
            }
            return false;
        };
    }
}
