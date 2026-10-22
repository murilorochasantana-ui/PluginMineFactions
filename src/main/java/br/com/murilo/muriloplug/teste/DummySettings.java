package br.com.murilo.muriloplug.teste;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;

/** Leitura (sempre ao vivo) das opções {@code teste.*} do config.yml. */
public final class DummySettings {

    private static final Map<String, Object> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put("teste.ativado", true);
        DEFAULTS.put("teste.boneco-coloca-em-combate", true);
        DEFAULTS.put("teste.aliado-vida-inicial", 10);
        DEFAULTS.put("teste.aliado-volta-segundos", 3);
        DEFAULTS.put("teste.volta-posicao-segundos", 3);
        DEFAULTS.put("teste.dano-janela-segundos", 10);
    }

    private final JavaPlugin plugin;

    public DummySettings(JavaPlugin plugin) {
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

    /** false: o comando /boneco fica desligado e nenhum boneco é criado. */
    public boolean enabled() {
        return plugin.getConfig().getBoolean("teste.ativado", true);
    }

    public boolean combatOnHit() {
        return plugin.getConfig().getBoolean("teste.boneco-coloca-em-combate", true);
    }

    public double allyStartHealth() {
        return Math.max(0.5, Math.min(20, number("teste.aliado-vida-inicial")));
    }

    public long allyResetMillis() {
        return (long) (Math.max(0, number("teste.aliado-volta-segundos")) * 1000);
    }

    public long returnMillis() {
        return (long) (Math.max(0, number("teste.volta-posicao-segundos")) * 1000);
    }

    public long damageWindowMillis() {
        return (long) (Math.max(1, number("teste.dano-janela-segundos")) * 1000);
    }
}
