package br.com.murilo.muriloplug.claim;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public final class MyClaimsCommand implements CommandExecutor {

    private final ClaimManager claims;

    public MyClaimsCommand(ClaimManager claims) {
        this.claims = claims;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Apenas jogadores podem usar este comando.");
            return true;
        }
        List<Claim> list = claims.getByOwner(player.getUniqueId());
        if (list.isEmpty()) {
            player.sendMessage("§cVocê não possui claims. Use /claim <tamanho> para criar um.");
        } else {
            ClaimsMenu.open(player, list);
        }
        return true;
    }
}
