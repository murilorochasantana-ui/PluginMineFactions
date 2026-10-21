package br.com.murilo.muriloplug.classes;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** /kit admin classe: abre o menu com os kits das classes (para testes e para colocar na loja). */
public final class KitCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "mineplug.kit.admin";

    private final ClassItems items;

    public KitCommand(ClassItems items) {
        this.items = items;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Apenas jogadores podem usar este comando.");
            return true;
        }
        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage("§cVocê não tem permissão.");
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("classe")) {
            KitMenu.open(player, items);
        } else {
            player.sendMessage("§cUso: /" + label + " admin classe");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> options = List.of();
        if (args.length == 1) {
            options = List.of("admin");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            options = List.of("classe");
        }
        return options.stream().filter(option -> option.startsWith(typed)).toList();
    }
}
