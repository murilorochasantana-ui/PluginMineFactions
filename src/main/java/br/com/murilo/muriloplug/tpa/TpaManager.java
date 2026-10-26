package br.com.murilo.muriloplug.tpa;

import br.com.murilo.muriloplug.combat.CombatManager;
import br.com.murilo.muriloplug.teleport.TeleportCountdown;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Pedidos de teleporte entre jogadores (/tpa e /tpaqui). */
public final class TpaManager implements Listener {

    public static final int REQUEST_SECONDS = 60;
    public static final int REQUEST_COOLDOWN_SECONDS = 10;

    /** GO: o remetente vai até o destinatário (/tpa). BRING: o destinatário vai até o remetente (/tpaqui). */
    public enum Type { GO, BRING }

    private static final class Request {
        final UUID sender;
        final UUID target;
        final Type type;
        final long order;
        BukkitTask expiry;

        Request(UUID sender, UUID target, Type type, long order) {
            this.sender = sender;
            this.target = target;
            this.type = type;
            this.order = order;
        }
    }

    private final JavaPlugin plugin;
    private final CombatManager combat;
    private final TeleportCountdown countdown;
    private final File file;

    /** Pedido enviado por cada jogador (no máximo um). */
    private final Map<UUID, Request> sent = new HashMap<>();
    private final Map<UUID, Long> lastSentAt = new HashMap<>();
    private final Set<UUID> blocked = new HashSet<>();
    private long nextOrder;

    public TpaManager(JavaPlugin plugin, CombatManager combat, TeleportCountdown countdown) {
        this.plugin = plugin;
        this.combat = combat;
        this.countdown = countdown;
        this.file = new File(plugin.getDataFolder(), "tpa.yml");
    }

    // ---------------------------------------------------------------- enviar

    public void send(Player sender, Player target, Type type) {
        if (target.equals(sender)) {
            sender.sendMessage("§cVocê não pode enviar um pedido para si mesmo.");
            return;
        }
        if (blocked.contains(target.getUniqueId())) {
            sender.sendMessage("§cEste jogador não aceita TPA.");
            return;
        }
        Long last = lastSentAt.get(sender.getUniqueId());
        long now = System.currentTimeMillis();
        if (last != null && now - last < REQUEST_COOLDOWN_SECONDS * 1000L) {
            long wait = (long) Math.ceil((REQUEST_COOLDOWN_SECONDS * 1000L - (now - last)) / 1000.0);
            sender.sendMessage("§cAguarde " + wait + "s para enviar outro pedido.");
            return;
        }

        // Um novo pedido substitui o anterior.
        Request old = sent.remove(sender.getUniqueId());
        if (old != null) {
            old.expiry.cancel();
        }
        lastSentAt.put(sender.getUniqueId(), now);

        Request request = new Request(sender.getUniqueId(), target.getUniqueId(), type, nextOrder++);
        request.expiry = plugin.getServer().getScheduler().runTaskLater(plugin, () -> expire(request),
                REQUEST_SECONDS * 20L);
        sent.put(sender.getUniqueId(), request);

        sender.sendMessage("§aPedido enviado para §f" + target.getName() + "§a. Ele expira em " + REQUEST_SECONDS
                + " segundos. Use /tpa cancelar para cancelar.");
        target.sendMessage(type == Type.GO
                ? "§e" + sender.getName() + " quer se teleportar até você."
                : "§e" + sender.getName() + " quer que você vá até ele.");
        target.sendMessage(buttons(sender.getName()));
    }

    private Component buttons(String senderName) {
        Component accept = Component.text("[ACEITAR]", NamedTextColor.GREEN, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/tpa aceitar " + senderName))
                .hoverEvent(HoverEvent.showText(Component.text("Clique para aceitar o pedido", NamedTextColor.GREEN)));
        Component deny = Component.text("[NEGAR]", NamedTextColor.RED, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/tpa negar " + senderName))
                .hoverEvent(HoverEvent.showText(Component.text("Clique para negar o pedido", NamedTextColor.RED)));
        return accept.append(Component.text("  ")).append(deny);
    }

    private void expire(Request request) {
        if (sent.get(request.sender) != request) {
            return;
        }
        sent.remove(request.sender);
        Player sender = Bukkit.getPlayer(request.sender);
        Player target = Bukkit.getPlayer(request.target);
        if (sender != null) {
            sender.sendMessage("§cSeu pedido de TPA" + (target != null ? " para " + target.getName() : "") + " expirou.");
        }
        if (target != null) {
            target.sendMessage("§cO pedido de TPA" + (sender != null ? " de " + sender.getName() : "") + " expirou.");
        }
    }

    // ------------------------------------------------- aceitar / negar / cancelar

    /** Aceita o pedido de {@code senderName}, ou o último recebido se for null. */
    public void accept(Player receiver, String senderName) {
        Request request = find(receiver, senderName);
        if (request == null) {
            noRequest(receiver, senderName);
            return;
        }
        Player requester = Bukkit.getPlayer(request.sender);
        if (requester == null) {
            remove(request);
            receiver.sendMessage("§cO jogador que enviou o pedido saiu do servidor.");
            return;
        }

        Player traveler = request.type == Type.GO ? requester : receiver;
        Player destination = request.type == Type.GO ? receiver : requester;
        if (combat.isInCombat(traveler)) {
            // O pedido continua pendente: dá para aceitar de novo quando o combate acabar.
            Player other = traveler.equals(receiver) ? requester : receiver;
            traveler.sendMessage("§cVocê está em combate! Aguarde " + combat.remainingSeconds(traveler) + "s.");
            other.sendMessage("§c" + traveler.getName() + " está em combate e não pode se teleportar agora.");
            return;
        }

        remove(request);
        requester.sendMessage("§a" + receiver.getName() + " aceitou o seu pedido.");
        receiver.sendMessage("§aVocê aceitou o pedido de §f" + requester.getName() + "§a.");

        traveler.sendMessage("§eTeleportando para §f" + destination.getName() + "§e em "
                + TeleportCountdown.COUNTDOWN_SECONDS + " segundos. Não se mova!");
        UUID destinationId = destination.getUniqueId();
        countdown.start(traveler, () -> complete(traveler, destinationId));
    }

    private void complete(Player traveler, UUID destinationId) {
        Player destination = Bukkit.getPlayer(destinationId);
        if (destination == null) {
            traveler.sendMessage("§cO jogador saiu do servidor. Teleporte cancelado.");
            return;
        }
        if (combat.isInCombat(traveler)) {
            traveler.sendMessage("§cVocê está em combate! Aguarde " + combat.remainingSeconds(traveler) + "s.");
            return;
        }
        // Vai para a posição ATUAL do outro jogador, que pode ter andado durante a contagem.
        traveler.teleport(destination.getLocation());
        traveler.sendMessage("§aTeleportado para §f" + destination.getName() + "§a.");
    }

    public void deny(Player receiver, String senderName) {
        Request request = find(receiver, senderName);
        if (request == null) {
            noRequest(receiver, senderName);
            return;
        }
        remove(request);
        Player requester = Bukkit.getPlayer(request.sender);
        receiver.sendMessage("§aPedido negado.");
        if (requester != null) {
            requester.sendMessage("§c" + receiver.getName() + " negou o seu pedido de TPA.");
        }
    }

    public void cancel(Player sender) {
        Request request = sent.get(sender.getUniqueId());
        if (request == null) {
            sender.sendMessage("§cVocê não tem nenhum pedido enviado.");
            return;
        }
        remove(request);
        sender.sendMessage("§aSeu pedido de TPA foi cancelado.");
        Player target = Bukkit.getPlayer(request.target);
        if (target != null) {
            target.sendMessage("§e" + sender.getName() + " cancelou o pedido de TPA.");
        }
    }

    private void noRequest(Player receiver, String senderName) {
        receiver.sendMessage(senderName == null
                ? "§cVocê não tem pedidos de TPA pendentes."
                : "§cVocê não tem um pedido de TPA de " + senderName + ".");
    }

    /** Pedido recebido por {@code receiver}: o de {@code senderName}, ou o mais recente se for null. */
    private Request find(Player receiver, String senderName) {
        Request best = null;
        for (Request request : sent.values()) {
            if (!request.target.equals(receiver.getUniqueId())) {
                continue;
            }
            if (senderName != null) {
                Player sender = Bukkit.getPlayer(request.sender);
                if (sender == null || !sender.getName().equalsIgnoreCase(senderName)) {
                    continue;
                }
            }
            if (best == null || request.order > best.order) {
                best = request;
            }
        }
        return best;
    }

    private void remove(Request request) {
        request.expiry.cancel();
        sent.remove(request.sender, request);
    }

    /** Nomes de quem enviou pedidos para o jogador (para o tab-complete). */
    public List<String> requesterNames(Player receiver) {
        List<String> names = new ArrayList<>();
        for (Request request : sent.values()) {
            Player sender = Bukkit.getPlayer(request.sender);
            if (request.target.equals(receiver.getUniqueId()) && sender != null) {
                names.add(sender.getName());
            }
        }
        return names;
    }

    // ---------------------------------------------------------------- bloquear

    /** Liga/desliga o recebimento de pedidos. */
    public void toggleBlock(Player player) {
        if (blocked.remove(player.getUniqueId())) {
            player.sendMessage("§aVocê voltou a receber pedidos de TPA.");
        } else {
            blocked.add(player.getUniqueId());
            player.sendMessage("§eVocê não receberá mais pedidos de TPA.");
        }
        save();
    }

    public void load() {
        blocked.clear();
        if (!file.exists()) {
            return;
        }
        for (String text : YamlConfiguration.loadConfiguration(file).getStringList("blocked")) {
            try {
                blocked.add(UUID.fromString(text));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("UUID inválido em tpa.yml, ignorado: " + text);
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("blocked", blocked.stream().map(UUID::toString).toList());
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Não foi possível salvar tpa.yml: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- saída

    /** Quem sai do servidor tem apagados os pedidos que enviou e os que recebeu. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        String name = event.getPlayer().getName();

        Request own = sent.remove(id);
        if (own != null) {
            own.expiry.cancel();
            Player target = Bukkit.getPlayer(own.target);
            if (target != null) {
                target.sendMessage("§e" + name + " saiu do servidor e o pedido de TPA foi cancelado.");
            }
        }

        Iterator<Request> iterator = sent.values().iterator();
        while (iterator.hasNext()) {
            Request request = iterator.next();
            if (request.target.equals(id)) {
                request.expiry.cancel();
                iterator.remove();
                Player sender = Bukkit.getPlayer(request.sender);
                if (sender != null) {
                    sender.sendMessage("§e" + name + " saiu do servidor e o seu pedido de TPA foi cancelado.");
                }
            }
        }
    }

    public boolean isBlocked(UUID player) {
        return blocked.contains(player);
    }

    /** Apaga o bloqueio de TPA e os pedidos enviados e recebidos pelo jogador (reset de conta). */
    public void forget(UUID player) {
        lastSentAt.remove(player);
        sent.values().removeIf(request -> {
            boolean involved = request.sender.equals(player) || request.target.equals(player);
            if (involved && request.expiry != null) {
                request.expiry.cancel();
            }
            return involved;
        });
        if (blocked.remove(player)) {
            save();
        }
    }
}
