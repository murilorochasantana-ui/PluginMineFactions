package br.com.murilo.muriloplug.home;

import br.com.murilo.muriloplug.claim.ClaimManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class HomeCommand implements CommandExecutor, TabCompleter {

    private final HomeManager homes;
    private final HomeTeleportManager teleports;
    private final ClaimManager claims;

    public HomeCommand(HomeManager homes, HomeTeleportManager teleports, ClaimManager claims) {
        this.homes = homes;
        this.teleports = teleports;
        this.claims = claims;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Apenas jogadores podem usar este comando.");
            return true;
        }
        if (args.length > 1) {
            player.sendMessage("§cUso: /" + label + " [nome]");
            return true;
        }

        List<Home> list = homes.getByOwner(player.getUniqueId());
        if (args.length == 0) {
            if (list.isEmpty()) {
                player.sendMessage("§cVocê não possui homes. Use /sethome <nome> para criar uma.");
            } else {
                HomesMenu.open(player, list, claims);
            }
            return true;
        }

        Home home = homes.getByName(player.getUniqueId(), args[0]);
        if (home == null) {
            player.sendMessage("§cVocê não possui uma home chamada " + args[0] + ". Veja suas homes com /home.");
        } else {
            teleports.start(player, home);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (Home home : homes.getByOwner(player.getUniqueId())) {
            names.add(home.name());
        }
        return names;
    }
}
