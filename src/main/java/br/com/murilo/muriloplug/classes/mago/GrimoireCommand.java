package br.com.murilo.muriloplug.classes.mago;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /grimorio: abre o menu do Grimório (só Magos). Em combate o comando fica bloqueado por combate.comandos-bloqueados. */
public final class GrimoireCommand implements CommandExecutor {

    private final ManaManager mana;
    private final GrimoireManager grimoire;
    private final MagoSettings settings;

    public GrimoireCommand(ManaManager mana, GrimoireManager grimoire, MagoSettings settings) {
        this.mana = mana;
        this.grimoire = grimoire;
        this.settings = settings;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Apenas jogadores podem usar este comando.");
            return true;
        }
        if (!mana.isMage(player.getUniqueId())) {
            player.sendMessage("§cSomente Magos podem usar o Grimório.");
            return true;
        }
        GrimoireMenu.open(player, grimoire, settings);
        return true;
    }
}
