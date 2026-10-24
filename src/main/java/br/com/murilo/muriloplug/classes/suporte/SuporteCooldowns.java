package br.com.murilo.muriloplug.classes.suporte;

import br.com.murilo.muriloplug.teste.TestMode;
import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Recarga de cada habilidade do Suporte, por jogador (action bar; as habilidades do clique principal também animam o
 * item).
 */
public final class SuporteCooldowns {

    private final Map<UUID, Map<SuporteSkill, Long>> until = new HashMap<>();
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

    public long remainingMillis(UUID id, SuporteSkill skill) {
        if (test.isActive(id)) {
            return 0;
        }
        Map<SuporteSkill, Long> own = until.get(id);
        Long end = own == null ? null : own.get(skill);
        return end == null ? 0 : Math.max(0, end - System.currentTimeMillis());
    }

    /** Segundos restantes (arredondados para cima), ou 0. */
    public int remainingSeconds(UUID id, SuporteSkill skill) {
        return (int) Math.ceil(remainingMillis(id, skill) / 1000.0);
    }

    /** True se a habilidade pode ser usada; senão avisa "Recarga: Xs" na action bar. */
    public boolean check(Player player, SuporteSkill skill) {
        long remaining = remainingMillis(player.getUniqueId(), skill);
        if (remaining <= 0) {
            return true;
        }
        player.sendActionBar(ShopUi.text("§c" + skill.displayName() + ": recarga "
                + (int) Math.ceil(remaining / 1000.0) + "s"));
        return false;
    }

    public void start(Player player, SuporteSkill skill, double seconds) {
        if (seconds <= 0 || test.isActive(player)) {
            return;
        }
        until.computeIfAbsent(player.getUniqueId(), k -> new EnumMap<>(SuporteSkill.class))
                .put(skill, System.currentTimeMillis() + (long) (seconds * 1000));
        if (skill.item() != null) {
            player.setCooldown(skill.item().material(), (int) Math.ceil(seconds * 20));
        }
    }

    public void forget(UUID id) {
        until.remove(id);
    }
}
