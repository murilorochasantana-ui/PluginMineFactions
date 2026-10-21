package br.com.murilo.muriloplug.shop;

import br.com.murilo.muriloplug.economy.EconomyManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public final class LojaCommand implements CommandExecutor, TabCompleter {

    private final ShopManager shop;
    private final EconomyManager economy;

    public LojaCommand(ShopManager shop, EconomyManager economy) {
        this.shop = shop;
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Apenas jogadores podem usar este comando.");
            return true;
        }

        if (args.length == 0) {
            if (shop.size() == 0) {
                player.sendMessage("§cA loja está vazia no momento.");
            } else {
                ShopMenu.open(player, shop, economy, ShopMenu.Mode.BUY, 0);
            }
        } else if (args.length == 1 && args[0].equalsIgnoreCase("admin")) {
            if (!player.hasPermission(ShopMenusListener.ADMIN_PERMISSION)) {
                player.sendMessage("§cVocê não tem permissão.");
            } else {
                ShopMenu.open(player, shop, economy, ShopMenu.Mode.ADMIN, 0);
            }
        } else {
            player.sendMessage("§cUso: /" + label);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission(ShopMenusListener.ADMIN_PERMISSION)) {
            return List.of("admin");
        }
        return List.of();
    }
}
