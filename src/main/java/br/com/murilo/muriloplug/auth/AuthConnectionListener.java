package br.com.murilo.muriloplug.auth;

import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Entrada e saída: recusa nicks duplicados e com maiúsculas trocadas ANTES do Minecraft aceitar a conexão
 * (o padrão do jogo é expulsar quem já estava online, o que deixaria alguém derrubar outro jogador) e
 * cuida do estado de login de quem entra e sai.
 */
public final class AuthConnectionListener implements Listener {

    private final AuthManager auth;

    public AuthConnectionListener(AuthManager auth) {
        this.auth = auth;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String refusal = auth.checkConnection(event.getName());
        if (refusal != null) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.text(refusal));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        auth.handleJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        auth.handleQuit(event.getPlayer());
    }
}
