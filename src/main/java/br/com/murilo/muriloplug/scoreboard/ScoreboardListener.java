package br.com.murilo.muriloplug.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ScoreboardListener implements Listener {

    private final PlayerScoreboards boards;
    private final SidebarManager sidebar;
    private final NametagManager nametags;

    public ScoreboardListener(PlayerScoreboards boards, SidebarManager sidebar, NametagManager nametags) {
        this.boards = boards;
        this.sidebar = sidebar;
        this.nametags = nametags;
    }

    /** Configura quem já estava online quando o plugin foi ligado (ex: /reload). */
    public void setupOnlinePlayers() {
        Bukkit.getOnlinePlayers().forEach(this::setup);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        setup(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        nametags.removePlayer(player);
        sidebar.forget(player);
        boards.remove(player);
    }

    private void setup(Player player) {
        boards.create(player);
        sidebar.setup(player);
        nametags.addPlayer(player);
    }
}
