package br.com.murilo.muriloplug.claim;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class ClaimCommand implements CommandExecutor, TabCompleter {

    private static final long REMOVE_CONFIRM_MILLIS = 30_000L;
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_-]{3,16}");
    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final Set<String> RESERVED = Set.of("remover", "cancelar", "info", "confirmar", "ok", "trust", "untrust");

    private record PendingRemoval(String name, long expiresAt) {}

    private final ClaimManager claims;
    private final ClaimPreviewManager previews;
    private final Map<UUID, PendingRemoval> pendingRemovals = new HashMap<>();

    public ClaimCommand(ClaimManager claims, ClaimPreviewManager previews) {
        this.claims = claims;
        this.previews = previews;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Apenas jogadores podem usar este comando.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("remover")) {
            remove(player, args);
        } else if (args.length > 0 && args[0].equalsIgnoreCase("trust")) {
            trust(player, args);
        } else if (args.length > 0 && args[0].equalsIgnoreCase("untrust")) {
            untrust(player, args);
        } else if (args.length == 1) {
            switch (args[0].toLowerCase()) {
                case "cancelar" -> cancel(player);
                case "info" -> info(player);
                default -> {
                    if (DIGITS.matcher(args[0]).matches()) {
                        preview(player, args[0]);
                    } else {
                        confirm(player, args[0]);
                    }
                }
            }
        } else {
            player.sendMessage("§cUso: /" + label + " <tamanho 1-" + Claim.MAX_SIZE
                    + "> | <nome> | cancelar | remover <nome> | trust <jogador> <claim> | untrust <jogador> <claim> | info");
        }
        return true;
    }

    private void preview(Player player, String sizeText) {
        int size;
        try {
            size = Integer.parseInt(sizeText);
        } catch (NumberFormatException e) {
            size = -1;
        }
        if (size < 1 || size > Claim.MAX_SIZE) {
            player.sendMessage("§cO tamanho deve ser entre 1 e " + Claim.MAX_SIZE + " blocos.");
            return;
        }
        if (claims.getByOwner(player.getUniqueId()).size() >= ClaimManager.MAX_CLAIMS) {
            player.sendMessage("§cVocê já atingiu o limite de " + ClaimManager.MAX_CLAIMS
                    + " claims. Remova um com /claim remover <nome>.");
            return;
        }
        Claim area = Claim.centeredOn(player.getUniqueId(), player.getLocation(), size);
        if (!isFree(player, area)) {
            return;
        }
        previews.show(player, area);
        player.sendMessage("§eÁrea " + size + "x" + size
                + " (X " + area.minX() + " a " + area.maxX()
                + ", Z " + area.minZ() + " a " + area.maxZ() + ") marcada em amarelo."
                + " Digite §a/claim <nome>§e para confirmar ou §c/claim cancelar§e. Expira em 60 segundos.");
    }

    private void confirm(Player player, String name) {
        Claim area = previews.get(player.getUniqueId());
        if (area == null) {
            player.sendMessage("§cVocê não tem uma pré-visualização. Use /claim <tamanho> primeiro.");
            return;
        }
        String nameError = validateName(player, name);
        if (nameError != null) {
            player.sendMessage("§c" + nameError);
            return;
        }
        previews.cancel(player);
        if (claims.getByOwner(player.getUniqueId()).size() >= ClaimManager.MAX_CLAIMS) {
            player.sendMessage("§cVocê já atingiu o limite de " + ClaimManager.MAX_CLAIMS + " claims. Nada foi criado.");
            return;
        }
        if (!isFree(player, area)) {
            return;
        }
        claims.add(area.withName(name));
        player.sendMessage("§aClaim §f" + name + "§a criado! Área " + area.width() + "x" + area.depth()
                + " (X " + area.minX() + " a " + area.maxX()
                + ", Z " + area.minZ() + " a " + area.maxZ() + "), em todas as alturas.");
    }

    /** Retorna a mensagem de erro, ou null se o nome for válido. */
    private String validateName(Player player, String name) {
        if (RESERVED.contains(name.toLowerCase())) {
            return "Este nome é uma palavra reservada. Escolha outro.";
        }
        if (!VALID_NAME.matcher(name).matches()) {
            return "O nome deve ter de 3 a 16 caracteres, apenas letras, números, \"_\" e \"-\".";
        }
        if (DIGITS.matcher(name).matches()) {
            return "O nome não pode ser só números.";
        }
        if (claims.getByName(player.getUniqueId(), name) != null) {
            return "Você já tem um claim com esse nome.";
        }
        return null;
    }

    /** Avisa o jogador e retorna false se a área colidir com algum claim. */
    private boolean isFree(Player player, Claim area) {
        Claim overlap = claims.findOverlap(area);
        if (overlap == null) {
            return true;
        }
        if (overlap.owner().equals(player.getUniqueId())) {
            player.sendMessage("§cEsta área colide com o seu claim §f" + overlap.name() + "§c. Afaste-se e tente novamente.");
        } else {
            player.sendMessage("§cEsta área colide com o claim de outro jogador. Afaste-se e tente novamente.");
        }
        return false;
    }

    private void cancel(Player player) {
        if (previews.cancel(player)) {
            player.sendMessage("§aPré-visualização cancelada.");
        } else {
            player.sendMessage("§cVocê não tem uma pré-visualização.");
        }
    }

    private void remove(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage("§cUso: /claim remover <nome>");
            return;
        }
        if (args[1].equalsIgnoreCase("confirmar")) {
            confirmRemoval(player);
            return;
        }
        Claim claim = claims.getByName(player.getUniqueId(), args[1]);
        if (claim == null) {
            player.sendMessage("§cVocê não possui um claim chamado " + args[1] + ". Veja seus claims com /claim info.");
            return;
        }
        pendingRemovals.put(player.getUniqueId(),
                new PendingRemoval(claim.name(), System.currentTimeMillis() + REMOVE_CONFIRM_MILLIS));
        player.sendMessage("§eTem certeza que deseja remover o claim §f" + claim.name()
                + "§e? Digite §c/claim remover confirmar§e em até 30 segundos.");
    }

    private void confirmRemoval(Player player) {
        PendingRemoval pending = pendingRemovals.remove(player.getUniqueId());
        if (pending == null) {
            player.sendMessage("§cVocê não tem nenhuma remoção pendente. Use /claim remover <nome>.");
        } else if (System.currentTimeMillis() > pending.expiresAt()) {
            player.sendMessage("§cA confirmação expirou. Use /claim remover <nome> novamente.");
        } else if (claims.remove(player.getUniqueId(), pending.name())) {
            player.sendMessage("§aO claim §f" + pending.name() + "§a foi removido.");
        } else {
            player.sendMessage("§cO claim " + pending.name() + " não existe mais.");
        }
    }

    private void trust(Player player, String[] args) {
        if (args.length != 3) {
            player.sendMessage("§cUso: /claim trust <jogador> <nome do claim>");
            return;
        }
        Claim claim = claims.getByName(player.getUniqueId(), args[2]);
        if (claim == null) {
            player.sendMessage("§cVocê não possui um claim chamado " + args[2] + ". Veja seus claims com /claim info.");
            return;
        }
        Player online = Bukkit.getPlayerExact(args[1]);
        OfflinePlayer target = online != null ? online : Bukkit.getOfflinePlayerIfCached(args[1]);
        if (target == null || (online == null && !target.hasPlayedBefore())) {
            player.sendMessage("§cJogador " + args[1] + " não encontrado. Ele precisa estar online ou já ter entrado no servidor.");
            return;
        }
        String targetName = target.getName() != null ? target.getName() : args[1];
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage("§cVocê não pode dar trust a si mesmo.");
            return;
        }
        if (claim.isTrusted(target.getUniqueId())) {
            player.sendMessage("§c" + targetName + " já tem trust no claim " + claim.name() + ".");
            return;
        }
        claims.update(claim.withTrusted(target.getUniqueId()));
        player.sendMessage("§a" + targetName + " agora pode construir no claim §f" + claim.name() + "§a.");
        if (online != null) {
            online.sendMessage("§a" + player.getName() + " deu a você permissão para construir no claim §f"
                    + claim.name() + "§a.");
        }
    }

    private void untrust(Player player, String[] args) {
        if (args.length != 3) {
            player.sendMessage("§cUso: /claim untrust <jogador> <nome do claim>");
            return;
        }
        Claim claim = claims.getByName(player.getUniqueId(), args[2]);
        if (claim == null) {
            player.sendMessage("§cVocê não possui um claim chamado " + args[2] + ". Veja seus claims com /claim info.");
            return;
        }
        for (UUID id : claim.trusted()) {
            String name = Bukkit.getOfflinePlayer(id).getName();
            if (args[1].equalsIgnoreCase(name)) {
                claims.update(claim.withoutTrusted(id));
                player.sendMessage("§a" + name + " não pode mais construir no claim §f" + claim.name() + "§a.");
                Player online = Bukkit.getPlayer(id);
                if (online != null) {
                    online.sendMessage("§c" + player.getName() + " removeu sua permissão no claim §f"
                            + claim.name() + "§c.");
                }
                return;
            }
        }
        player.sendMessage("§c" + args[1] + " não tinha trust no claim " + claim.name() + ".");
    }

    private void info(Player player) {
        List<Claim> list = claims.getByOwner(player.getUniqueId());
        if (list.isEmpty()) {
            player.sendMessage("§cVocê não possui claims.");
            return;
        }
        player.sendMessage("§eSeus claims (" + list.size() + "/" + ClaimManager.MAX_CLAIMS + "):");
        for (Claim claim : list) {
            player.sendMessage("§e- §f" + claim.name() + "§e: mundo " + claim.world()
                    + ", X " + claim.minX() + " a " + claim.maxX()
                    + ", Z " + claim.minZ() + " a " + claim.maxZ()
                    + " (" + claim.width() + "x" + claim.depth() + ")");
            player.sendMessage("§7   Amigos: " + (claim.trusted().isEmpty()
                    ? "nenhum" : String.join(", ", claim.trustedNames())));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("cancelar", "remover", "trust", "untrust", "info", "10", "25", "50");
        }
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        String sub = args[0].toLowerCase();
        if (args.length == 2) {
            switch (sub) {
                case "remover" -> {
                    List<String> options = claimNames(player);
                    options.add("confirmar");
                    return options;
                }
                case "trust" -> {
                    return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                }
                case "untrust" -> {
                    Set<String> friends = new LinkedHashSet<>();
                    for (Claim claim : claims.getByOwner(player.getUniqueId())) {
                        friends.addAll(claim.trustedNames());
                    }
                    return new ArrayList<>(friends);
                }
                default -> {
                    return List.of();
                }
            }
        }
        if (args.length == 3 && (sub.equals("trust") || sub.equals("untrust"))) {
            return claimNames(player);
        }
        return List.of();
    }

    private List<String> claimNames(Player player) {
        List<String> names = new ArrayList<>();
        for (Claim claim : claims.getByOwner(player.getUniqueId())) {
            names.add(claim.name());
        }
        return names;
    }
}
