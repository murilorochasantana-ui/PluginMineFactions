package br.com.murilo.muriloplug.scoreboard;

import br.com.murilo.muriloplug.clan.Clan;
import br.com.murilo.muriloplug.clan.ClanManager;
import br.com.murilo.muriloplug.combat.CombatManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Tag do clan antes do nome e " [Combate]" depois dele (acima da cabeça e no TAB). Como cada jogador tem o próprio scoreboard,
 * o team de cada jogador precisa existir no scoreboard de todos os jogadores online.
 */
public final class NametagManager {

    private static final String SUFFIX = " [Combate]";

    private final PlayerScoreboards boards;
    private final CombatManager combat;
    private final ClanManager clans;
    /** Algo a mais depois de " [Combate]" (ex: o "$" vermelho de quem tem recompensa na cabeça). */
    private java.util.function.Function<java.util.UUID, Component> extraSuffix = id -> Component.empty();

    public NametagManager(PlayerScoreboards boards, CombatManager combat, ClanManager clans) {
        this.boards = boards;
        this.combat = combat;
        this.clans = clans;
    }

    public void setExtraSuffix(java.util.function.Function<java.util.UUID, Component> extraSuffix) {
        this.extraSuffix = extraSuffix;
    }

    /** Jogador entrou: cria no scoreboard dele os teams de todos e o team dele no scoreboard dos outros. */
    public void addPlayer(Player joined) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            apply(boards.get(joined), other);
            if (!other.equals(joined)) {
                apply(boards.get(other), joined);
            }
        }
    }

    /** Jogador saiu: remove o team dele dos scoreboards dos outros. */
    public void removePlayer(Player quit) {
        String teamName = teamName(quit);
        for (Player other : Bukkit.getOnlinePlayers()) {
            Scoreboard board = boards.get(other);
            Team team = board == null || other.equals(quit) ? null : board.getTeam(teamName);
            if (team != null) {
                team.unregister();
            }
        }
    }

    /** O combate ou o clan de {@code target} mudou: atualiza o prefixo e o sufixo dele no scoreboard de todos. */
    public void update(Player target) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            apply(boards.get(viewer), target);
        }
    }

    private void apply(Scoreboard board, Player target) {
        if (board == null) {
            return;
        }
        String name = teamName(target);
        Team team = board.getTeam(name);
        if (team == null) {
            team = board.registerNewTeam(name);
            team.addEntry(target.getName());
        }
        Clan clan = clans.getClan(target.getUniqueId());
        team.prefix(clan == null ? Component.empty() : Component.text("[" + clan.tag() + "] ", NamedTextColor.GOLD));
        team.suffix(Component.text(SUFFIX, combat.isInCombat(target) ? NamedTextColor.RED : NamedTextColor.GREEN)
                .append(extraSuffix.apply(target.getUniqueId())));
    }

    /** Nome do team baseado no UUID (16 caracteres, o limite do Minecraft). */
    private static String teamName(Player player) {
        return player.getUniqueId().toString().replace("-", "").substring(0, 16);
    }
}
