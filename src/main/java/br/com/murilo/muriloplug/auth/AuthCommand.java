package br.com.murilo.muriloplug.auth;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** /register, /login, /trocarsenha (com aliases em português) e /auth admin resetar. */
public final class AuthCommand implements CommandExecutor, TabCompleter {

    public static final String ADMIN_PERMISSION = "mineplug.auth.admin";

    private final AuthManager auth;

    public AuthCommand(AuthManager auth) {
        this.auth = auth;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("auth")) {
            admin(sender, args);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Apenas jogadores podem usar este comando.");
            return true;
        }

        switch (name) {
            case "register" -> {
                if (args.length != 2) {
                    player.sendMessage("§cUso: /" + label + " <senha> <senha>");
                } else {
                    auth.register(player, args[0], args[1]);
                }
            }
            case "login" -> {
                if (args.length != 1) {
                    player.sendMessage("§cUso: /" + label + " <senha>");
                } else {
                    auth.login(player, args[0]);
                }
            }
            case "trocarsenha" -> {
                if (args.length != 2) {
                    player.sendMessage("§cUso: /" + label + " <senha antiga> <senha nova>");
                } else {
                    auth.changePassword(player, args[0], args[1]);
                }
            }
            default -> { }
        }
        return true;
    }

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cVocê não tem permissão.");
            return;
        }
        if (args.length != 3 || !args[0].equalsIgnoreCase("admin") || !args[1].equalsIgnoreCase("resetar")) {
            sender.sendMessage("§cUso: /auth admin resetar <jogador>");
            return;
        }
        if (auth.reset(args[2])) {
            sender.sendMessage("§aO registro de " + args[2] + " foi apagado. Ele poderá se registrar de novo.");
        } else {
            sender.sendMessage("§cNão há registro para " + args[2] + ".");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // Nunca sugere nada nos campos de senha.
        if (!command.getName().equalsIgnoreCase("auth") || !sender.hasPermission(ADMIN_PERMISSION)) {
            return List.of();
        }
        return switch (args.length) {
            case 1 -> List.of("admin");
            case 2 -> List.of("resetar");
            case 3 -> auth.registeredNicks();
            default -> List.of();
        };
    }
}
