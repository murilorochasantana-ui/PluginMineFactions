package br.com.murilo.muriloplug.teste;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** /boneco: cria e controla os bonecos de teste (só administradores, permissão mineplug.boneco). */
public final class BonecoCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "mineplug.boneco";

    private static final List<String> SUBCOMMANDS = List.of("inimigo", "aliado", "remover", "removertodos", "vida",
            "dano", "armadura", "resetar");
    private static final List<String> ARMORS = List.of("nenhuma", "couro", "ferro", "diamante", "netherite");
    private static final double AIM_RANGE = 30;
    private static final double NEAR_RANGE = 5;
    private static final String USAGE = "§cUso: /boneco <inimigo|aliado|remover|removertodos|vida <0.5-20>|dano <valor>"
            + "|armadura <nenhuma|couro|ferro|diamante|netherite>|resetar>";

    private final DummyManager manager;
    private final DummySettings settings;

    public BonecoCommand(DummyManager manager, DummySettings settings) {
        this.manager = manager;
        this.settings = settings;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSomente jogadores podem usar este comando.");
            return true;
        }
        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage("§cVocê não tem permissão.");
            return true;
        }
        if (!settings.enabled()) {
            player.sendMessage("§cOs bonecos de teste estão desligados (teste.ativado: false no config.yml).");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(USAGE);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "inimigo" -> create(player, TargetRelations.DummyType.INIMIGO);
            case "aliado" -> create(player, TargetRelations.DummyType.ALIADO);
            case "remover" -> remove(player);
            case "removertodos" -> player.sendMessage("§aBonecos removidos: " + manager.removeAll() + ".");
            case "vida" -> health(player, args);
            case "dano" -> damage(player, args);
            case "armadura" -> armor(player, args);
            case "resetar" -> reset(player);
            default -> player.sendMessage(USAGE);
        }
        return true;
    }

    private void create(Player player, TargetRelations.DummyType type) {
        // Onde o OP está, virado para ele.
        Location at = player.getLocation();
        at.setYaw(at.getYaw() + 180f);
        at.setPitch(0);
        manager.create(type, at);
        player.sendMessage("§aBoneco " + type.id() + " criado.");
    }

    /** O boneco que o jogador mira; sem mira, o mais próximo (até 5 blocos). */
    private Dummy target(Player player) {
        Dummy dummy = manager.aimed(player, AIM_RANGE);
        if (dummy == null) {
            dummy = manager.nearest(player, NEAR_RANGE);
        }
        if (dummy == null) {
            player.sendMessage("§cNenhum boneco na mira nem a até " + (int) NEAR_RANGE + " blocos.");
        }
        return dummy;
    }

    private void remove(Player player) {
        // Remover: o mais próximo (até 5 blocos) ou o que está sendo mirado.
        Dummy dummy = manager.nearest(player, NEAR_RANGE);
        if (dummy == null) {
            dummy = manager.aimed(player, AIM_RANGE);
        }
        if (dummy == null) {
            player.sendMessage("§cNenhum boneco a até " + (int) NEAR_RANGE + " blocos nem na mira.");
            return;
        }
        manager.remove(dummy);
        player.sendMessage("§aBoneco removido.");
    }

    private static Double number(Player player, String[] args, String usage) {
        if (args.length != 2) {
            player.sendMessage(usage);
            return null;
        }
        try {
            double value = Double.parseDouble(args[1].replace(',', '.'));
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException e) {
            player.sendMessage("§cValor inválido: " + args[1]);
            return null;
        }
    }

    private void health(Player player, String[] args) {
        Double value = number(player, args, "§cUso: /boneco vida <0.5 a 20>");
        if (value == null) {
            return;
        }
        if (value < 0.5 || value > DummyManager.MAX_HEALTH) {
            player.sendMessage("§cA vida deve ficar entre 0.5 e 20.");
            return;
        }
        Dummy dummy = target(player);
        if (dummy != null) {
            manager.setHealth(dummy, value);
            player.sendMessage("§aVida do boneco: " + DummyHud.format(value) + "/20.");
        }
    }

    private void damage(Player player, String[] args) {
        Double value = number(player, args, "§cUso: /boneco dano <valor>");
        if (value == null) {
            return;
        }
        if (value <= 0) {
            player.sendMessage("§cO dano deve ser maior que 0.");
            return;
        }
        Dummy dummy = target(player);
        if (dummy != null) {
            dummy.entity.damage(value); // sem causador: não conta como golpe de ninguém
            player.sendMessage("§aDano sem causador no boneco: " + DummyHud.format(value) + ".");
        }
    }

    private void armor(Player player, String[] args) {
        String armor = args.length == 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (!ARMORS.contains(armor)) {
            player.sendMessage("§cUso: /boneco armadura <nenhuma|couro|ferro|diamante|netherite>");
            return;
        }
        Dummy dummy = target(player);
        if (dummy != null) {
            manager.setArmor(dummy, armor);
            player.sendMessage("§aArmadura do boneco: " + armor + ".");
        }
    }

    private void reset(Player player) {
        Dummy dummy = target(player);
        if (dummy != null) {
            manager.reset(dummy);
            player.sendMessage("§aBoneco resetado.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> options = List.of();
        if (args.length == 1) {
            options = SUBCOMMANDS;
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "armadura" -> options = ARMORS;
                case "vida" -> options = List.of("0.5", "5", "10", "20");
                case "dano" -> options = List.of("1", "5", "10", "20");
                default -> { }
            }
        }
        return options.stream().filter(option -> option.startsWith(typed)).toList();
    }
}
