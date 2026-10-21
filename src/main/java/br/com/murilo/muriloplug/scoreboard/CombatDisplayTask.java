package br.com.murilo.muriloplug.scoreboard;

import br.com.murilo.muriloplug.combat.CombatManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/** Roda a cada segundo: avisa e atualiza a tag de quem saiu de combate e atualiza o sidebar de todos. */
public final class CombatDisplayTask extends BukkitRunnable {

    private final CombatManager combat;
    private final NametagManager nametags;
    private final SidebarManager sidebar;

    public CombatDisplayTask(CombatManager combat, NametagManager nametags, SidebarManager sidebar) {
        this.combat = combat;
        this.nametags = nametags;
        this.sidebar = sidebar;
    }

    @Override
    public void run() {
        for (Player player : combat.collectExpired()) {
            nametags.update(player);
            player.sendActionBar(Component.text("Você saiu de combate", NamedTextColor.GREEN));
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            sidebar.update(player);
        }
    }
}
