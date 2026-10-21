package br.com.murilo.muriloplug.clan;

import br.com.murilo.muriloplug.shop.ShopUi;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Convites para clan: expiram em {@link #INVITE_SECONDS} segundos e têm botões clicáveis no chat. */
public final class ClanInvites implements Listener {

    public static final int INVITE_SECONDS = 60;

    /** Convite recebido por um jogador: de qual clan, quem convidou e a tarefa que o expira. */
    public record Invite(String tag, UUID inviter, BukkitTask expiry) {}

    private final JavaPlugin plugin;
    private final ClanManager clans;
    /** Convites de cada jogador, do mais antigo para o mais recente. */
    private final Map<UUID, List<Invite>> invites = new HashMap<>();

    public ClanInvites(JavaPlugin plugin, ClanManager clans) {
        this.plugin = plugin;
        this.clans = clans;
    }

    /** Envia o convite (substitui um convite anterior do mesmo clan para o mesmo jogador). */
    public void send(Player inviter, Player target, Clan clan) {
        UUID targetId = target.getUniqueId();
        take(targetId, clan.tag());

        BukkitTask expiry = plugin.getServer().getScheduler().runTaskLater(plugin, () -> expire(targetId, clan.tag()),
                INVITE_SECONDS * 20L);
        invites.computeIfAbsent(targetId, k -> new ArrayList<>()).add(new Invite(clan.tag(), inviter.getUniqueId(), expiry));

        inviter.sendMessage("§aConvite enviado para §f" + target.getName() + "§a. Ele expira em " + INVITE_SECONDS + " segundos.");
        target.sendMessage("§e" + inviter.getName() + " convidou você para o clan §6[" + clan.tag() + "] " + clan.name() + "§e.");
        Component accept = Component.text("[ACEITAR]", NamedTextColor.GREEN, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/clan aceitar " + clan.tag()))
                .hoverEvent(HoverEvent.showText(Component.text("Clique para entrar no clan", NamedTextColor.GREEN)));
        Component deny = Component.text("[NEGAR]", NamedTextColor.RED, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/clan negar " + clan.tag()))
                .hoverEvent(HoverEvent.showText(Component.text("Clique para recusar o convite", NamedTextColor.RED)));
        target.sendMessage(accept.append(Component.text("  ")).append(deny));
    }

    /** Convite da TAG informada, ou o mais recente se a TAG for null. Não remove o convite. */
    public Invite find(UUID invitee, String tag) {
        List<Invite> list = invites.get(invitee);
        if (list == null || list.isEmpty()) {
            return null;
        }
        if (tag == null) {
            return list.get(list.size() - 1);
        }
        for (Invite invite : list) {
            if (invite.tag().equalsIgnoreCase(tag)) {
                return invite;
            }
        }
        return null;
    }

    /** Remove e devolve o convite da TAG (cancelando a expiração), ou null. */
    public Invite take(UUID invitee, String tag) {
        Invite invite = find(invitee, tag);
        if (invite == null) {
            return null;
        }
        invite.expiry().cancel();
        List<Invite> list = invites.get(invitee);
        list.remove(invite);
        if (list.isEmpty()) {
            invites.remove(invitee);
        }
        return invite;
    }

    /** Apaga todos os convites do jogador. */
    public void clear(UUID invitee) {
        List<Invite> list = invites.remove(invitee);
        if (list != null) {
            list.forEach(invite -> invite.expiry().cancel());
        }
    }

    /** TAGs dos convites recebidos (para o tab-complete). */
    public List<String> tags(UUID invitee) {
        List<String> tags = new ArrayList<>();
        for (Invite invite : invites.getOrDefault(invitee, List.of())) {
            tags.add(invite.tag());
        }
        return tags;
    }

    private void expire(UUID targetId, String tag) {
        Invite invite = find(targetId, tag);
        if (invite == null) {
            return;
        }
        take(targetId, tag);
        Player target = Bukkit.getPlayer(targetId);
        Player inviter = Bukkit.getPlayer(invite.inviter());
        if (target != null) {
            target.sendMessage("§cO convite do clan [" + tag + "] expirou.");
        }
        if (inviter != null) {
            inviter.sendMessage("§cO convite para " + ClanManager.nameOf(targetId) + " expirou.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer().getUniqueId());
    }
}
