package br.com.murilo.muriloplug.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Scoreboard individual de cada jogador, compartilhado entre a barra lateral e as tags de nome. */
public final class PlayerScoreboards {

    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    /** Cria um scoreboard novo para o jogador e já o aplica a ele. */
    public Scoreboard create(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        boards.put(player.getUniqueId(), board);
        player.setScoreboard(board);
        return board;
    }

    public Scoreboard get(Player player) {
        return boards.get(player.getUniqueId());
    }

    public void remove(Player player) {
        boards.remove(player.getUniqueId());
    }
}
