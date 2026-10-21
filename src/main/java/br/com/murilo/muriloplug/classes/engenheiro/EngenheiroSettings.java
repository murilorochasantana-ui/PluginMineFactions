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

    private static final Map<String, Object> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put(AREA_SIZE, 20);
        DEFAULTS.put(AREA_HEIGHT, 20);
        DEFAULTS.put(COMBAT_WAIT, 60);
        DEFAULTS.put(MAX_MINUTES, 120);
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
