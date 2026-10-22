package br.com.murilo.muriloplug.xp;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Leitura (sempre ao vivo) das opções {@code xp.*} do config.yml. */
public final class XpSettings {

    public static final String DISABLED_PATH = "xp.desativado";
    public static final String BLOCK_ENCHANTING_PATH = "xp.bloquear-mesa-encantamento";
    public static final String BLOCK_ANVIL_PATH = "xp.bloquear-bigorna";

    private final JavaPlugin plugin;

    public XpSettings(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Acrescenta ao config as chaves que faltarem, sem apagar nada. Retorna true se alterou. */
    public static boolean addMissing(FileConfiguration config) {
        boolean changed = false;
        for (String path : new String[]{DISABLED_PATH, BLOCK_ENCHANTING_PATH, BLOCK_ANVIL_PATH}) {
            if (!config.contains(path)) {
                config.set(path, true);
                changed = true;
            }
        }
        return changed;
    }

    /** true: o servidor não tem XP (e a barra de XP é a barra de recurso das classes). */
    public boolean disabled() {
        return plugin.getConfig().getBoolean(DISABLED_PATH, true);
    }

    public boolean blockEnchantingTable() {
        return plugin.getConfig().getBoolean(BLOCK_ENCHANTING_PATH, true);
    }

    public boolean blockAnvil() {
        return plugin.getConfig().getBoolean(BLOCK_ANVIL_PATH, true);
    }
}
