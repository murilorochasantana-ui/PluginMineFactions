package br.com.murilo.muriloplug.combat;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;

/** Bloqueia em combate os comandos listados em {@code combate.comandos-bloqueados} do config.yml (lido ao vivo). */
public final class CombatCommandListener implements Listener {

    public static final String PATH = "combate.comandos-bloqueados";

    private final JavaPlugin plugin;
    private final CombatManager combat;

    public CombatCommandListener(JavaPlugin plugin, CombatManager combat) {
        this.plugin = plugin;
        this.combat = combat;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!combat.isInCombat(player)) {
            return;
        }
        String label = event.getMessage().trim();
        label = label.startsWith("/") ? label.substring(1) : label;
        int space = label.indexOf(' ');
        if (space >= 0) {
            label = label.substring(0, space);
        }
        int namespace = label.indexOf(':'); // "mineplug:grimorio" também conta
        if (namespace >= 0) {
            label = label.substring(namespace + 1);
        }
        label = label.toLowerCase(Locale.ROOT);
        List<String> blocked = plugin.getConfig().getStringList(PATH);
        for (String command : blocked) {
            if (command.trim().replaceFirst("^/", "").toLowerCase(Locale.ROOT).equals(label)) {
                event.setCancelled(true);
                player.sendMessage("§cVocê não pode usar /" + label + " em combate.");
                return;
            }
        }
    }
}
