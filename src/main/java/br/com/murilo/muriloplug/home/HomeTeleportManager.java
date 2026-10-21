package br.com.murilo.muriloplug.home;

import br.com.murilo.muriloplug.claim.ClaimManager;
import br.com.murilo.muriloplug.combat.CombatManager;
import br.com.murilo.muriloplug.teleport.TeleportCountdown;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Teleporte para home: valida combate, mundo e claim, e usa a contagem compartilhada de 5s. */
public final class HomeTeleportManager {

    private static final String BLOCKED_MESSAGE = "§cEsta home está dentro do claim de outro jogador.";

    private final TeleportCountdown countdown;
    private final ClaimManager claims;
    private final CombatManager combat;

    public HomeTeleportManager(TeleportCountdown countdown, ClaimManager claims, CombatManager combat) {
        this.countdown = countdown;
        this.claims = claims;
        this.combat = combat;
    }

    /**
     * Inicia a contagem, substituindo a anterior do jogador. Retorna false se o jogador estiver em combate,
     * se o mundo da home não existir ou se o destino estiver no claim de outro jogador.
     */
    public boolean start(Player player, Home home) {
        if (combat.isInCombat(player)) {
            player.sendMessage("§cVocê está em combate! Aguarde " + combat.remainingSeconds(player) + "s.");
            return false;
        }
        Location destination = home.toLocation();
        if (destination == null) {
            player.sendMessage("§cO mundo da home " + home.name() + " não existe mais.");
            return false;
        }
        if (!claims.canBuild(player, destination)) {
            player.sendMessage(BLOCKED_MESSAGE);
            return false;
        }

        player.sendMessage("§eTeleportando para a home §f" + home.name() + "§e em " + TeleportCountdown.COUNTDOWN_SECONDS
                + " segundos. Não se mova!");
        countdown.start(player, () -> teleport(player, home));
        return true;
    }

    private void teleport(Player player, Home home) {
        Location location = home.toLocation();
        if (location == null) {
            player.sendMessage("§cO mundo da home " + home.name() + " não existe mais.");
            return;
        }
        // O claim pode ter sido criado, ou o trust removido, durante a contagem.
        if (!claims.canBuild(player, location)) {
            player.sendMessage(BLOCKED_MESSAGE);
            return;
        }
        player.teleport(location);
        player.sendMessage("§aTeleportado para a home §f" + home.name() + "§a.");
    }
}
