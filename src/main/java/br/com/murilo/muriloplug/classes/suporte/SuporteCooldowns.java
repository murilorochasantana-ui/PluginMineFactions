package br.com.murilo.muriloplug.classes.suporte;

import br.com.murilo.muriloplug.teste.TestMode;
import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Recarga de cada item do Suporte, por jogador (action bar + animação no item). */
public final class SuporteCooldowns {

    private final Map<UUID, Map<SuporteItem, Long>> until = new HashMap<>();
    private TestMode test = TestMode.inactive();

    /** Modo Teste dos administradores: sem custos nem recargas. */
    public void setTestMode(TestMode test) {
        this.test = test;
    }

    /** Ao ligar o Modo Teste: zera as recargas em andamento e a animação nos itens. */
    public void clearAll(Player player) {
        forget(player.getUniqueId());
        for (SuporteItem type : SuporteItem.values()) {
            player.setCooldown(type.material(), 0);
        }
    }

    public long remainingMillis(UUID id, SuporteItem type) {
        Map<SuporteItem, Long> own = until.get(id);
        Long end = own == null ? null : own.get(type);
        return end == null ? 0 : Math.max(0, end - System.currentTimeMillis());
    }

    /** True se o item pode ser usado; senão avisa "Recarga: Xs" na action bar. */
    public boolean check(Player player, SuporteItem type) {
        if (test.isActive(player)) {
            return true; // Modo Teste: sem recarga
        }
        long remaining = remainingMillis(player.getUniqueId(), type);
        if (remaining <= 0) {
            return true;
        }
        player.sendActionBar(ShopUi.text("§cRecarga: " + (int) Math.ceil(remaining / 1000.0) + "s"));
        return false;
    }

    public void start(Player player, SuporteItem type, double seconds) {
        if (seconds <= 0 || test.isActive(player)) {
            return;
        }
        until.computeIfAbsent(player.getUniqueId(), k -> new EnumMap<>(SuporteItem.class))
                .put(type, System.currentTimeMillis() + (long) (seconds * 1000));
        player.setCooldown(type.material(), (int) Math.ceil(seconds * 20));
    }

    public void forget(UUID id) {
        until.remove(id);
    }
}
