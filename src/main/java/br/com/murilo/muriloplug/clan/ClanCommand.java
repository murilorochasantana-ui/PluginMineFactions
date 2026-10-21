package br.com.murilo.muriloplug.clan;

import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.shop.ShopUi;
import br.com.murilo.muriloplug.stats.StatsManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Atende /clan (com subcomandos) e o atalho /c (chat do clan). */
public final class ClanCommand implements CommandExecutor, TabCompleter {

    private static final Pattern TAG_PATTERN = Pattern.compile("[A-Za-z0-9]{2,4}");
    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9 _-]{3,16}");
    private static final Set<String> RESERVED_TAGS = Set.of(
            "criar", "convidar", "aceitar", "negar", "sair", "expulsar", "lider", "desfazer", "chat", "info");
    private static final List<String> SUBCOMMANDS = List.of(
            "criar", "convidar", "aceitar", "negar", "sair", "expulsar", "lider", "desfazer", "chat", "info");

    private final ClanManager clans;
    private final ClanInvites invites;
    private final StatsManager stats;

    private final ClassManager classes;

    public ClanCommand(ClanManager clans, ClanInvites invites, StatsManager stats, ClassManager classes) {
        this.classes = classes;
        this.clans = clans;
        this.invites = invites;
        this.stats = stats;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Apenas jogadores podem usar este comando.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("c")) {
            chat(player, args);
            return true;
        }
        if (args.length == 0) {
            Clan own = clans.getClan(player.getUniqueId());
            if (own == null) {
                help(player);
            } else {
                ClanInfoMenu.open(player, own, clans, stats, classes);
            }
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "criar" -> create(player, args);
            case "convidar" -> invite(player, args);
            case "aceitar" -> accept(player, args);
            case "negar" -> deny(player, args);
            case "sair" -> leave(player);
            case "expulsar" -> kick(player, args);
            case "lider" -> transfer(player, args);
            case "desfazer" -> disband(player);
            case "chat" -> chat(player, Arrays.copyOfRange(args, 1, args.length));
            case "info" -> info(player, args);
            default -> help(player);
        }
        return true;
    }

    private void help(Player player) {
        player.sendMessage("§6Comandos do clan:");
        player.sendMessage("§e/clan criar <TAG> <nome> §7- cria um clan");
        player.sendMessage("§e/clan convidar <jogador> §7- convida um jogador (líder)");
        player.sendMessage("§e/clan aceitar [TAG] §7- aceita um convite");
        player.sendMessage("§e/clan negar [TAG] §7- recusa um convite");
        player.sendMessage("§e/clan sair §7- sai do clan");
        player.sendMessage("§e/clan expulsar <jogador> §7- expulsa um membro (líder)");
        player.sendMessage("§e/clan lider <jogador> §7- passa a liderança (líder)");
        player.sendMessage("§e/clan desfazer §7- apaga o clan (líder)");
        player.sendMessage("§e/clan chat <mensagem> §7ou §e/c <mensagem> §7- chat do clan");
        player.sendMessage("§e/clan info [TAG] §7- mostra um clan");
    }

    // ------------------------------------------------------------------ criar

    private void create(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUso: /clan criar <TAG> <nome>");
            return;
        }
        if (clans.getClan(player.getUniqueId()) != null) {
            player.sendMessage("§cVocê já está em um clan.");
            return;
        }
        String tag = args[1];
        if (RESERVED_TAGS.contains(tag.toLowerCase(Locale.ROOT))) {
            player.sendMessage("§cEsta TAG é uma palavra reservada. Escolha outra.");
            return;
        }
        if (!TAG_PATTERN.matcher(tag).matches()) {
            player.sendMessage("§cA TAG deve ter de 2 a 4 caracteres, apenas letras e números.");
            return;
        }
        if (clans.getByTag(tag) != null) {
            player.sendMessage("§cJá existe um clan com a TAG " + tag.toUpperCase(Locale.ROOT) + ".");
            return;
        }
        String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim();
        if (!NAME_PATTERN.matcher(name).matches()) {
            player.sendMessage("§cO nome deve ter de 3 a 16 caracteres: letras, números, espaço, \"_\" e \"-\".");
            return;
        }

        Clan clan = clans.create(tag, name, player.getUniqueId());
        Bukkit.broadcast(ShopUi.text("§e" + player.getName() + " §7criou o clan §6[" + clan.tag() + "] " + clan.name()));
    }

    // ---------------------------------------------------------------- convites

    private void invite(Player player, String[] args) {
        Clan clan = leaderClan(player);
        if (clan == null) {
            return;
        }
        if (args.length != 2) {
            player.sendMessage("§cUso: /clan convidar <jogador>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage("§cO jogador " + args[1] + " não está online.");
            return;
        }
        if (clans.getClan(target.getUniqueId()) != null) {
            player.sendMessage("§c" + target.getName() + " já está em um clan.");
            return;
        }
        if (clans.isFull(clan)) {
            player.sendMessage("§cO clan está cheio (" + clan.size() + "/" + clans.maxMembers() + ").");
            return;
        }
        invites.send(player, target, clan);
    }

    private void accept(Player player, String[] args) {
        if (args.length > 2) {
            player.sendMessage("§cUso: /clan aceitar [TAG]");
            return;
        }
        UUID id = player.getUniqueId();
        ClanInvites.Invite invite = invites.find(id, args.length == 2 ? args[1] : null);
        if (invite == null) {
            player.sendMessage("§cVocê não tem convites de clan pendentes.");
            return;
        }
        invites.take(id, invite.tag());

        if (clans.getClan(id) != null) {
            player.sendMessage("§cVocê já está em um clan.");
            return;
        }
        // Confere de novo: o clan pode ter sido desfeito ou ter enchido desde o convite.
        Clan clan = clans.getByTag(invite.tag());
        if (clan == null) {
            player.sendMessage("§cEste clan não existe mais.");
            return;
        }
        if (!clans.addMember(clan, id)) {
            player.sendMessage("§cO clan está cheio.");
            return;
        }
        invites.clear(id);
        clans.broadcast(clan, "§e" + player.getName() + " §7entrou no clan.");
    }

    private void deny(Player player, String[] args) {
        if (args.length > 2) {
            player.sendMessage("§cUso: /clan negar [TAG]");
            return;
        }
        ClanInvites.Invite invite = invites.find(player.getUniqueId(), args.length == 2 ? args[1] : null);
        if (invite == null) {
            player.sendMessage("§cVocê não tem convites de clan pendentes.");
            return;
        }
        invites.take(player.getUniqueId(), invite.tag());
        player.sendMessage("§aConvite do clan [" + invite.tag() + "] recusado.");
        Player inviter = Bukkit.getPlayer(invite.inviter());
        if (inviter != null) {
            inviter.sendMessage("§c" + player.getName() + " recusou o convite para o clan.");
        }
    }

    // ------------------------------------------------------------- sair / líder

    private void leave(Player player) {
        Clan clan = clans.getClan(player.getUniqueId());
        if (clan == null) {
            player.sendMessage("§cVocê não está em um clan.");
            return;
        }
        if (clans.isLeader(player.getUniqueId())) {
            if (clan.size() > 1) {
                player.sendMessage("§cVocê é o líder e ainda há outros membros. "
                        + "Use /clan lider <jogador> para passar a liderança ou /clan desfazer para apagar o clan.");
                return;
            }
            // Líder sozinho: sair é o mesmo que desfazer.
            clans.disband(clan);
            player.sendMessage("§aVocê era o único membro: o clan §6[" + clan.tag() + "] " + clan.name() + "§a foi desfeito.");
            return;
        }
        clans.removeMember(clan, player.getUniqueId());
        player.sendMessage("§aVocê saiu do clan §6[" + clan.tag() + "] " + clan.name() + "§a.");
        clans.broadcast(clan, "§e" + player.getName() + " §7saiu do clan.");
    }

    private void kick(Player player, String[] args) {
        Clan clan = leaderClan(player);
        if (clan == null) {
            return;
        }
        if (args.length != 2) {
            player.sendMessage("§cUso: /clan expulsar <jogador>");
            return;
        }
        UUID target = memberByName(clan, args[1]);
        if (target == null) {
            player.sendMessage("§c" + args[1] + " não é membro do seu clan.");
        } else if (target.equals(player.getUniqueId())) {
            player.sendMessage("§cVocê não pode se expulsar. Use /clan sair ou /clan desfazer.");
        } else {
            ClanConfirmMenu.open(player, ClanConfirmMenu.Action.KICK, clan, target);
        }
    }

    private void transfer(Player player, String[] args) {
        Clan clan = leaderClan(player);
        if (clan == null) {
            return;
        }
        if (args.length != 2) {
            player.sendMessage("§cUso: /clan lider <jogador>");
            return;
        }
        UUID target = memberByName(clan, args[1]);
        if (target == null) {
            player.sendMessage("§c" + args[1] + " não é membro do seu clan.");
        } else if (target.equals(player.getUniqueId())) {
            player.sendMessage("§cVocê já é o líder.");
        } else {
            ClanConfirmMenu.open(player, ClanConfirmMenu.Action.TRANSFER, clan, target);
        }
    }

    private void disband(Player player) {
        Clan clan = leaderClan(player);
        if (clan != null) {
            ClanConfirmMenu.open(player, ClanConfirmMenu.Action.DISBAND, clan, null);
        }
    }

    // ------------------------------------------------------------------- chat

    private void chat(Player player, String[] words) {
        Clan clan = clans.getClan(player.getUniqueId());
        if (clan == null) {
            player.sendMessage("§cVocê não está em um clan.");
            return;
        }
        if (words.length == 0) {
            player.sendMessage("§cUso: /c <mensagem>");
            return;
        }
        // O texto do jogador entra como texto puro (sem interpretar códigos de cor).
        clans.send(clan, ShopUi.text("§6[Clan] §e" + player.getName() + "§7: §f")
                .append(net.kyori.adventure.text.Component.text(String.join(" ", words))));
    }

    // ------------------------------------------------------------------- info

    private void info(Player player, String[] args) {
        if (args.length > 2) {
            player.sendMessage("§cUso: /clan info [TAG]");
            return;
        }
        Clan clan;
        if (args.length == 2) {
            clan = clans.getByTag(args[1]);
            if (clan == null) {
                player.sendMessage("§cNão existe um clan com a TAG " + args[1].toUpperCase(Locale.ROOT) + ".");
                return;
            }
        } else {
            clan = clans.getClan(player.getUniqueId());
            if (clan == null) {
                help(player);
                return;
            }
        }
        ClanInfoMenu.open(player, clan, clans, stats, classes);
    }

    // --------------------------------------------------------------- auxiliares

    /** Clan do jogador se ele for o líder; senão avisa e retorna null. */
    private Clan leaderClan(Player player) {
        Clan clan = clans.getClan(player.getUniqueId());
        if (clan == null) {
            player.sendMessage("§cVocê não está em um clan.");
            return null;
        }
        if (!clans.isLeader(player.getUniqueId())) {
            player.sendMessage("§cApenas o líder do clan pode usar este comando.");
            return null;
        }
        return clan;
    }

    /** Membro do clan pelo nome (online ou offline), ou null. */
    private static UUID memberByName(Clan clan, String name) {
        for (UUID member : clan.members()) {
            if (ClanManager.nameOf(member).equalsIgnoreCase(name)) {
                return member;
            }
        }
        return null;
    }

    // -------------------------------------------------------------- tab-complete

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || command.getName().equalsIgnoreCase("c")) {
            return List.of();
        }
        if (args.length == 1) {
            return SUBCOMMANDS;
        }
        if (args.length != 2) {
            return List.of();
        }

        UUID id = player.getUniqueId();
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "convidar" -> {
                return Bukkit.getOnlinePlayers().stream()
                        .filter(online -> !online.equals(player) && clans.getClan(online.getUniqueId()) == null)
                        .map(Player::getName).toList();
            }
            case "expulsar", "lider" -> {
                Clan clan = clans.getClan(id);
                List<String> names = new ArrayList<>();
                if (clan != null) {
                    for (UUID member : clan.members()) {
                        if (!member.equals(id)) {
                            names.add(ClanManager.nameOf(member));
                        }
                    }
                }
                return names;
            }
            case "aceitar", "negar" -> {
                return invites.tags(id);
            }
            case "info" -> {
                return clans.all().stream().map(Clan::tag).toList();
            }
            default -> {
                return List.of();
            }
        }
    }
}
