package br.com.murilo.muriloplug.classes;

import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Silêncio (habilidade do Suporte): quem está silenciado não usa NENHUMA habilidade de classe (magias, míssil,
 * habilidades e itens de classe com ação própria); ataques normais, comer e andar continuam. É a verificação central
 * que todos os pontos de ativação das classes consultam, como o Modo Teste: {@link #blocks(Player)} antes de ativar.
 * Fica só em memória.
 */
public final class SilenceManager {

    private static final Map<UUID, Long> silencedUntil = new HashMap<>();

    private SilenceManager() {
    }

    public static boolean isSilenced(UUID id) {
        Long until = silencedUntil.get(id);
        if (until == null) {
            return false;
        }
        if (until <= System.currentTimeMillis()) {
            silencedUntil.remove(id);
            return false;
        }
        return true;
    }

    public static boolean isSilenced(Player player) {
        return isSilenced(player.getUniqueId());
    }

    /** Segundos restantes (arredondados para cima), ou 0. */
    public static int remainingSeconds(UUID id) {
        Long until = silencedUntil.get(id);
        long left = until == null ? 0 : until - System.currentTimeMillis();
        return left <= 0 ? 0 : (int) Math.ceil(left / 1000.0);
    }

    /**
     * True (e avisa "Você está silenciado! (Xs)" na action bar) se o jogador está silenciado: quem chama não ativa a
     * habilidade.
     */
    public static boolean blocks(Player player) {
        if (!isSilenced(player)) {
            return false;
        }
        player.sendActionBar(ShopUi.text("§8Você está silenciado! (" + remainingSeconds(player.getUniqueId()) + "s)"));
        return true;
    }

    public static void silence(Entity entity, double seconds) {
        silencedUntil.put(entity.getUniqueId(), System.currentTimeMillis() + (long) (seconds * 1000));
    }

    /** Tira o silêncio (Purificar, morte, saída). */
    public static void clear(UUID id) {
        silencedUntil.remove(id);
    }

    /** Quem está silenciado agora (para as partículas). */
    public static Set<UUID> silenced() {
        silencedUntil.values().removeIf(until -> until <= System.currentTimeMillis());
        return Set.copyOf(silencedUntil.keySet());
    }

    /** Ninguém fica silenciado (onDisable). */
    public static void clearAll() {
        silencedUntil.clear();
    }
}
