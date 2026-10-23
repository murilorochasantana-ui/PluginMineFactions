package br.com.murilo.muriloplug.classes.arqueiro;

import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.shop.ShopUi;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Linhas do sidebar do Arqueiro (null para quem não é Arqueiro): ✔ verde se der para usar (Foco suficiente),
 * ✖ vermelho se não, e o custo em cinza.
 */
public final class ArqueiroSidebar {

    private final ClassManager classes;
    private final ArqueiroSettings settings;
    private final FocusManager focus;
    private final ShotModes modes;
    private final UltraFocus ultra;

    public ArqueiroSidebar(ClassManager classes, ArqueiroSettings settings, FocusManager focus, ShotModes modes,
                           UltraFocus ultra) {
        this.classes = classes;
        this.settings = settings;
        this.focus = focus;
        this.modes = modes;
        this.ultra = ultra;
    }

    private boolean isArcher(Player player) {
        return classes.hasClass(player.getUniqueId(), PlayerClass.ARQUEIRO);
    }

    /** "Foco: 42/100". */
    public Component focusLine(Player player) {
        if (!isArcher(player)) {
            return null;
        }
        return ShopUi.text("§eFoco: §f" + focus.get(player.getUniqueId()) + "/" + focus.max());
    }

    /** "Modo: 1 flecha [F]" (ou "Modo: Rajada [F] 7s" durante a recarga). */
    public Component modeLine(Player player) {
        if (!isArcher(player)) {
            return null;
        }
        UUID id = player.getUniqueId();
        String line = "§aModo: " + (modes.isBurst(id) ? "Rajada" : "1 flecha") + " §7[F]";
        int wait = modes.cooldownRemainingSeconds(id);
        return ShopUi.text(wait > 0 ? line + " §c" + wait + "s" : line);
    }

    public Component jumpLine(Player player) {
        return skill(player, "§fPulo Duplo §7[Pulo no ar] ", settings.jumpCost());
    }

    public Component dashLine(Player player) {
        return skill(player, "§bDash §7[Shift+F] ", settings.dashCost());
    }

    public Component rainLine(Player player) {
        return skill(player, "§9Chuva §7[Shift+Clique Esq] ", settings.rainCost());
    }

    public Component markLine(Player player) {
        return skill(player, "§cMarca §7[Clique Esq] ", settings.markCost());
    }

    /** "Ultra Foco [Q] ✔ (100)" ou "Ultra Foco [Q] ARMADO". */
    public Component ultraLine(Player player) {
        if (!isArcher(player)) {
            return null;
        }
        if (ultra.isArmed(player.getUniqueId())) {
            return ShopUi.text("§6Ultra Foco §7[Q] §6§lARMADO");
        }
        return skill(player, "§6Ultra Foco §7[Q] ", settings.ultraCost());
    }

    private Component skill(Player player, String label, int cost) {
        if (!isArcher(player)) {
            return null;
        }
        return ShopUi.text(label).append(mark(focus.has(player.getUniqueId(), cost)))
                .append(ShopUi.text(" §8(" + cost + ")"));
    }

    private static Component mark(boolean usable) {
        return usable ? Component.text("✔", NamedTextColor.GREEN) : Component.text("✖", NamedTextColor.RED);
    }
}
