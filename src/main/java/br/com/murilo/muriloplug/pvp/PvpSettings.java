package br.com.murilo.muriloplug.pvp;

import org.bukkit.configuration.file.FileConfiguration;

/** Opções do PvP estilo 1.8, lidas do config.yml (seção {@code pvp}). */
public record PvpSettings(boolean noAttackCooldown, boolean removeSweep, boolean axeDamage18, boolean disableShield) {

    public static final String NO_COOLDOWN_PATH = "pvp.sem-recarga-de-ataque";
    public static final String NO_SWEEP_PATH = "pvp.remover-ataque-em-area";
    public static final String AXE_PATH = "pvp.dano-machado-1-8";
    public static final String NO_SHIELD_PATH = "pvp.desativar-escudo";

    public static PvpSettings from(FileConfiguration config) {
        return new PvpSettings(
                config.getBoolean(NO_COOLDOWN_PATH, true),
                config.getBoolean(NO_SWEEP_PATH, true),
                config.getBoolean(AXE_PATH, true),
                config.getBoolean(NO_SHIELD_PATH, false));
    }
}
