package br.com.murilo.muriloplug.economy;

import br.com.murilo.muriloplug.shop.ShopUi;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Confirmações clicáveis no chat ("[CONFIRMAR]"). Cada jogador tem no máximo uma pendente por comando; o clique roda
 * {@code /<comando> confirmar <código>} e só vale com o código certo e dentro do prazo (cada código vale uma vez).
 */
public final class ChatConfirmations {

    private record Pending(String code, long expiresAt, Consumer<Player> action) {
    }

    private final String command;
    private final long ttlMillis;
    private final Map<UUID, Pending> pending = new HashMap<>();

    public ChatConfirmations(String command, int seconds) {
        this.command = command;
        this.ttlMillis = seconds * 1000L;
    }

    /** Guarda a ação e manda a pergunta com o botão [CONFIRMAR]. */
    public void ask(Player player, String question, Consumer<Player> action) {
        String code = Integer.toString(ThreadLocalRandom.current().nextInt(100_000, 1_000_000), 36);
        pending.put(player.getUniqueId(), new Pending(code, System.currentTimeMillis() + ttlMillis, action));
        Component button = ShopUi.text("§a§l[CONFIRMAR]")
                .clickEvent(ClickEvent.runCommand("/" + command + " confirmar " + code))
                .hoverEvent(HoverEvent.showText(ShopUi.text("§7Clique para confirmar")));
        player.sendMessage(ShopUi.text(question + " ").append(button)
                .append(ShopUi.text(" §8(vale por " + ttlMillis / 1000 + "s)")));
    }

    /** Roda a ação pendente se o código bater e não tiver vencido. */
    public void confirm(Player player, String code) {
        Pending entry = pending.get(player.getUniqueId());
        if (entry == null || !entry.code().equals(code)) {
            player.sendMessage("§cNão há nada para confirmar (ou o pedido mudou). Use o comando de novo.");
            return;
        }
        pending.remove(player.getUniqueId());
        if (entry.expiresAt() < System.currentTimeMillis()) {
            player.sendMessage("§cA confirmação venceu. Use o comando de novo.");
            return;
        }
        entry.action().accept(player);
    }

    public void forget(UUID player) {
        pending.remove(player);
    }
}
