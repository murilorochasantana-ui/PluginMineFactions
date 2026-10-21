package br.com.murilo.muriloplug.home;

import br.com.murilo.muriloplug.claim.ClaimManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.regex.Pattern;

public final class SetHomeCommand implements CommandExecutor {

    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_-]{3,16}");

    private final HomeManager homes;
    private final ClaimManager claims;

    public SetHomeCommand(HomeManager homes, ClaimManager claims) {
        this.homes = homes;
        this.claims = claims;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Apenas jogadores podem usar este comando.");
            return true;
        }
        if (args.length != 1) {
            player.sendMessage("§cUso: /" + label + " <nome>");
            return true;
        }

        String name = args[0];
        if (name.equalsIgnoreCase("confirmar")) {
            player.sendMessage("§cEste nome é uma palavra reservada. Escolha outro.");
            return true;
        }
        if (!VALID_NAME.matcher(name).matches()) {
            player.sendMessage("§cO nome deve ter de 3 a 16 caracteres, apenas letras, números, \"_\" e \"-\".");
            return true;
        }

        if (!claims.canBuild(player, player.getLocation())) {
            player.sendMessage("§cVocê não pode marcar uma home no claim de outro jogador.");
            return true;
        }

        Home existing = homes.getByName(player.getUniqueId(), name);
        String shownName = existing != null ? existing.name() : name;
        switch (homes.set(player.getUniqueId(), Home.at(name, player.getLocation()))) {
            case ADDED -> player.sendMessage("§aHome §f" + shownName + "§a criada ("
                    + homes.getByOwner(player.getUniqueId()).size() + "/" + HomeManager.MAX_HOMES + ").");
            case UPDATED -> player.sendMessage("§aHome §f" + shownName + "§a atualizada.");
            case LIMIT_REACHED -> player.sendMessage("§cVocê já atingiu o limite de " + HomeManager.MAX_HOMES
                    + " homes. Use /delhome <nome> para apagar uma.");
        }
        return true;
    }
}
