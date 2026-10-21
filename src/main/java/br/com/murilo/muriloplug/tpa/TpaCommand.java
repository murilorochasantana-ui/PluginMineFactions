package br.com.murilo.muriloplug.tpa;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Atende /tpa (com subcomandos) e /tpaqui. */
public final class TpaCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("aceitar", "negar", "cancelar", "bloquear");

    private final TpaManager tpa;

    public TpaCommand(TpaManager tpa) {
        this.tpa = tpa;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Apenas jogadores podem usar este comando.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("tpaqui")) {
            if (args.length != 1) {
                player.sendMessage("§cUso: /tpaqui <jogador>");
            } else {
                request(player, args[0], TpaManager.Type.BRING);
            }
            return true;
        }

        if (args.length == 0) {
            usage(player);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "aceitar" -> {
                if (args.length > 2) {
                    player.sendMessage("§cUso: /tpa aceitar [jogador]");
                } else {
                    tpa.accept(player, args.length == 2 ? args[1] : null);
                }
            }
            case "negar" -> {
                if (args.length > 2) {
                    player.sendMessage("§cUso: /tpa negar [jogador]");
                } else {
                    tpa.deny(player, args.length == 2 ? args[1] : null);
                }
            }
            case "cancelar" -> tpa.cancel(player);
            case "bloquear" -> tpa.toggleBlock(player);
            default -> {
                if (args.length == 1) {
                    request(player, args[0], TpaManager.Type.GO);
                } else {
                    usage(player);
                }
            }
        }
        return true;
    }

    private void request(Player player, String targetName, TpaManager.Type type) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            player.sendMessage("§cO jogador " + targetName + " não está online.");
            return;
        }
        tpa.send(player, target, type);
    }

    private void usage(Player player) {
        player.sendMessage("§cUso: /tpa <jogador> | aceitar [jogador] | negar [jogador] | cancelar | bloquear");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        boolean tpaqui = command.getName().equalsIgnoreCase("tpaqui");

        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (!tpaqui) {
                options.addAll(SUBCOMMANDS);
            }
            options.addAll(onlineNamesExcept(player));
            return options;
        }
        if (args.length == 2 && !tpaqui
                && (args[0].equalsIgnoreCase("aceitar") || args[0].equalsIgnoreCase("negar"))) {
            return tpa.requesterNames(player);
        }
        return List.of();
    }

    private static List<String> onlineNamesExcept(Player player) {
        return Bukkit.getOnlinePlayers().stream()
                .filter(online -> !online.equals(player))
                .map(Player::getName)
                .toList();
    }
}
