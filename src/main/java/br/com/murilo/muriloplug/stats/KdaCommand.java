package br.com.murilo.muriloplug.stats;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public final class KdaCommand implements CommandExecutor, TabCompleter {

    private final StatsManager stats;

    public KdaCommand(StatsManager stats) {
        this.stats = stats;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 1) {
            sender.sendMessage("§cUso: /" + label + " [jogador]");
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player player) {
                sender.sendMessage(line(player.getUniqueId()));
            } else {
                sender.sendMessage("Use /" + label + " <jogador>.");
            }
            return true;
        }

        Player online = Bukkit.getPlayerExact(args[0]);
        OfflinePlayer target = online != null ? online : Bukkit.getOfflinePlayerIfCached(args[0]);
        if (target == null || (online == null && !target.hasPlayedBefore())) {
            sender.sendMessage("§cJogador " + args[0] + " não encontrado. Ele precisa estar online ou já ter entrado no servidor.");
            return true;
        }
        String name = target.getName() != null ? target.getName() : args[0];
        sender.sendMessage("§eEstatísticas de §f" + name + "§e:");
        sender.sendMessage(line(target.getUniqueId()));
        return true;
    }

    private String line(UUID id) {
        return "§eK/D/A: §a" + stats.getKills(id) + "§7/§c" + stats.getDeaths(id) + "§7/§e" + stats.getAssists(id)
                + "§7  |  §eKDA: §f" + stats.formatKda(id);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }
}
