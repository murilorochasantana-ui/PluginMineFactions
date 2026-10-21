package br.com.murilo.muriloplug.market;

import br.com.murilo.muriloplug.economy.EconomyManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /mercado: abre o mercado (sem subcomandos; tudo é feito pelo menu). */
public final class MercadoCommand implements CommandExecutor {

    private final MarketManager market;
    private final EconomyManager economy;

    public MercadoCommand(MarketManager market, EconomyManager economy) {
        this.market = market;
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Apenas jogadores podem usar este comando.");
            return true;
        }
        if (args.length > 0) {
            player.sendMessage("§cUso: /" + label);
            return true;
        }
        MarketMenu.open(player, market, economy, 0);
        return true;
    }
}
