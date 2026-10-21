package br.com.murilo.muriloplug.clan;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** Chat público com a tag do clan: "[TAG] nome: mensagem" (ou "nome: mensagem" sem clan). Não afeta o /c. */
public final class ClanChatListener implements Listener {

    private final ClanManager clans;

    public ClanChatListener(ClanManager clans) {
        this.clans = clans;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        // O renderer roda fora da thread principal: ClanManager#getClan é seguro para isso (mapa concorrente).
        event.renderer(ChatRenderer.viewerUnaware((source, sourceDisplayName, message) -> {
            Component line = Component.empty();
            Clan clan = clans.getClan(source.getUniqueId());
            if (clan != null) {
                line = line.append(Component.text("[" + clan.tag() + "] ", NamedTextColor.GOLD));
            }
            return line.append(sourceDisplayName.colorIfAbsent(NamedTextColor.WHITE))
                    .append(Component.text(": ", NamedTextColor.GRAY))
                    .append(message.colorIfAbsent(NamedTextColor.WHITE));
        }));
    }
}
