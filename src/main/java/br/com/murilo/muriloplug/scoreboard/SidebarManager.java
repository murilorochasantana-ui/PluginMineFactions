package br.com.murilo.muriloplug.scoreboard;

import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.classes.arqueiro.ShotModes;
import br.com.murilo.muriloplug.classes.combatente.FuryManager;
import br.com.murilo.muriloplug.classes.engenheiro.EngenheiroSidebar;
import br.com.murilo.muriloplug.classes.mago.MagoSettings;
import br.com.murilo.muriloplug.classes.mago.ManaManager;
import br.com.murilo.muriloplug.combat.CombatManager;
import br.com.murilo.muriloplug.shop.ShopUi;
import br.com.murilo.muriloplug.clan.Clan;
import br.com.murilo.muriloplug.clan.ClanManager;
import br.com.murilo.muriloplug.economy.EconomyManager;
import br.com.murilo.muriloplug.stats.StatsManager;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Barra lateral de cada jogador. Cada linha vem de um método próprio: para adicionar uma informação nova
 * (dinheiro, clan etc.), crie um método {@code Component xxxLine(Player)} e inclua-o na lista {@link #lines}.
 */
public final class SidebarManager {

    private static final String OBJECTIVE = "sidebar";
    private static final Component TITLE = Component.text("MeuServer", NamedTextColor.GOLD, TextDecoration.BOLD);

    @FunctionalInterface
    private interface Line {
        Component render(Player player);
    }

    private final PlayerScoreboards boards;
    private final CombatManager combat;
    private final StatsManager stats;
    private final EconomyManager economy;
    private final ClanManager clans;
    private final ClassManager classes;
    private final ManaManager mana;
    private final MagoSettings magoSettings;
    private final ShotModes shotModes;
    private final FuryManager fury;
    private final EngenheiroSidebar engineer;
    /** Linhas na ordem de exibição, de cima para baixo. Uma linha que devolve null não aparece. */
    private final List<Line> lines = List.of(this::moneyLine, this::clanLine, this::classLine, this::modeLine,
            this::berserkLine, this::chargeLine, this::tauntLine, this::leapLine, this::energyLine, this::repairLine, this::hookLine, this::sentinelLine, this::manaLine, this::combatLine, this::killsLine, this::kdaLine);
    /** Último texto enviado para cada linha de cada jogador, para só reenviar o que mudou. */
    private final Map<UUID, Component[]> lastRendered = new HashMap<>();
    /** Última pontuação (posição) enviada para cada linha de cada jogador. */
    private final Map<UUID, int[]> lastScores = new HashMap<>();

    public SidebarManager(PlayerScoreboards boards, CombatManager combat, StatsManager stats,
                          EconomyManager economy, ClanManager clans, ClassManager classes, ManaManager mana,
                          ShotModes shotModes, FuryManager fury, MagoSettings magoSettings, EngenheiroSidebar engineer) {
        this.engineer = engineer;
        this.magoSettings = magoSettings;
        this.fury = fury;
        this.shotModes = shotModes;
        this.classes = classes;
        this.mana = mana;
        this.boards = boards;
        this.combat = combat;
        this.stats = stats;
        this.economy = economy;
        this.clans = clans;
    }

    /** Cria o objetivo do sidebar no scoreboard do jogador e desenha as linhas. */
    public void setup(Player player) {
        Scoreboard board = boards.get(player);
        if (board == null) {
            return;
        }
        Objective objective = board.registerNewObjective(OBJECTIVE, Criteria.DUMMY, TITLE);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());
        lastRendered.remove(player.getUniqueId());
        lastScores.remove(player.getUniqueId());
        update(player);
    }

    /** Redesenha as linhas que mudaram. */
    public void update(Player player) {
        Scoreboard board = boards.get(player);
        Objective objective = board == null ? null : board.getObjective(OBJECTIVE);
        if (objective == null) {
            return;
        }
        Component[] last = lastRendered.computeIfAbsent(player.getUniqueId(), id -> new Component[lines.size()]);
        int[] lastScore = lastScores.computeIfAbsent(player.getUniqueId(), id -> new int[lines.size()]);
        List<Component> rendered = new java.util.ArrayList<>();
        int visible = 0;
        for (Line line : lines) {
            Component text = line.render(player);
            rendered.add(text);
            if (text != null) {
                visible++;
            }
        }
        int position = visible;
        for (int i = 0; i < lines.size(); i++) {
            Component text = rendered.get(i);
            if (text == null) {
                if (last[i] != null) {
                    board.resetScores("line" + i);
                    last[i] = null;
                }
                continue;
            }
            int scoreValue = position--;
            if (text.equals(last[i]) && lastScore[i] == scoreValue) {
                continue;
            }
            Score score = objective.getScore("line" + i);
            score.setScore(scoreValue);
            score.customName(text);
            last[i] = text;
            lastScore[i] = scoreValue;
        }
    }

    public void forget(Player player) {
        lastRendered.remove(player.getUniqueId());
        lastScores.remove(player.getUniqueId());
    }

    /** Só aparece para Magos, e só com mago.mana-na-sidebar (a mana fica na barra de XP). */
    private Component manaLine(Player player) {
        UUID id = player.getUniqueId();
        if (!classes.hasClass(id, PlayerClass.MAGO) || !magoSettings.flag("mago.mana-na-sidebar")) {
            return null;
        }
        return Component.text("Mana: ", NamedTextColor.WHITE)
                .append(Component.text(mana.get(id) + "/" + mana.max(id), NamedTextColor.AQUA));
    }

    /** Só aparece para Arqueiros. */
    private Component modeLine(Player player) {
        UUID id = player.getUniqueId();
        if (!classes.hasClass(id, PlayerClass.ARQUEIRO)) {
            return null;
        }
        boolean burst = shotModes.isBurst(id);
        return Component.text("Modo: ", NamedTextColor.WHITE)
                .append(burst ? Component.text("rajada de 3", NamedTextColor.GOLD)
                        : Component.text("1 flecha", NamedTextColor.GREEN));
    }

    // ---- Habilidades do Combatente (só para Combatentes): ✔ verde se der para usar, ✖ vermelho se não.

    private Component berserkLine(Player player) {
        UUID id = player.getUniqueId();
        if (!classes.hasClass(id, PlayerClass.COMBATENTE)) {
            return null;
        }
        Component label = ShopUi.text("§cBerserker §7[Shift+F] ");
        if (fury.isBerserk(id)) {
            return label.append(ShopUi.text("§4ATIVO"));
        }
        return label.append(mark(fury.fury(id) >= fury.settings().berserkerMinimum()));
    }

    private Component chargeLine(Player player) {
        UUID id = player.getUniqueId();
        if (!classes.hasClass(id, PlayerClass.COMBATENTE)) {
            return null;
        }
        return ShopUi.text("§eInvestida §7[F] ")
                .append(mark(!fury.isBerserk(id) && fury.fury(id) >= fury.settings().dashCost()));
    }

    private Component tauntLine(Player player) {
        UUID id = player.getUniqueId();
        if (!classes.hasClass(id, PlayerClass.COMBATENTE)) {
            return null;
        }
        Component label = ShopUi.text("§6Provocar §7[Shift+Clique] ");
        int wait = fury.tauntCooldownSeconds(id);
        if (wait > 0) {
            return label.append(ShopUi.text("§c" + wait + "s"));
        }
        return label.append(mark(fury.fury(id) >= fury.settings().tauntCost()));
    }

    private Component leapLine(Player player) {
        UUID id = player.getUniqueId();
        if (!classes.hasClass(id, PlayerClass.COMBATENTE)) {
            return null;
        }
        return ShopUi.text("§fGolpe Pulando §7[Crítico] ")
                .append(mark(fury.fury(id) >= fury.settings().leapCost()));
    }

    // ---- Habilidades do Engenheiro (só para Engenheiros): as linhas ficam em EngenheiroSidebar.

    private Component energyLine(Player player) {
        return engineer.energyLine(player);
    }

    private Component repairLine(Player player) {
        return engineer.repairLine(player);
    }

    private Component hookLine(Player player) {
        return engineer.hookLine(player);
    }

    private Component sentinelLine(Player player) {
        return engineer.sentinelLine(player);
    }

    private static Component mark(boolean usable) {
        return usable ? Component.text("✔", NamedTextColor.GREEN) : Component.text("✖", NamedTextColor.RED);
    }

    private Component clanLine(Player player) {
        Clan clan = clans.getClan(player.getUniqueId());
        Component label = Component.text("Clan: ", NamedTextColor.WHITE);
        return clan == null
                ? label.append(Component.text("nenhum", NamedTextColor.GRAY))
                : label.append(Component.text("[" + clan.tag() + "]", NamedTextColor.GOLD));
    }

    private Component classLine(Player player) {
        PlayerClass playerClass = classes.getClass(player.getUniqueId());
        Component label = Component.text("Classe: ", NamedTextColor.WHITE);
        return playerClass == null
                ? label.append(Component.text("nenhuma", NamedTextColor.GRAY))
                : label.append(ShopUi.text(playerClass.coloredName()));
    }

    private Component moneyLine(Player player) {
        return Component.text("Dinheiro: ", NamedTextColor.WHITE)
                .append(Component.text(EconomyManager.format(economy.getBalance(player.getUniqueId())),
                        NamedTextColor.GREEN));
    }

    private Component combatLine(Player player) {
        Component label = Component.text("Combate: ", NamedTextColor.WHITE);
        if (combat.isInCombat(player)) {
            return label.append(Component.text("Sim (" + combat.remainingSeconds(player) + "s)", NamedTextColor.RED));
        }
        return label.append(Component.text("Não", NamedTextColor.GREEN));
    }

    private Component killsLine(Player player) {
        UUID id = player.getUniqueId();
        return Component.text("K/D/A: ", NamedTextColor.WHITE)
                .append(Component.text(stats.getKills(id), NamedTextColor.GREEN))
                .append(Component.text("/", NamedTextColor.GRAY))
                .append(Component.text(stats.getDeaths(id), NamedTextColor.RED))
                .append(Component.text("/", NamedTextColor.GRAY))
                .append(Component.text(stats.getAssists(id), NamedTextColor.YELLOW));
    }

    private Component kdaLine(Player player) {
        return Component.text("KDA: ", NamedTextColor.WHITE)
                .append(Component.text(stats.formatKda(player.getUniqueId()), NamedTextColor.WHITE));
    }
}
