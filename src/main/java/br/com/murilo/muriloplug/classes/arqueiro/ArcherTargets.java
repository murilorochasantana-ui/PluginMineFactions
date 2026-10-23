package br.com.murilo.muriloplug.classes.arqueiro;

import br.com.murilo.muriloplug.auth.AuthManager;
import br.com.murilo.muriloplug.teste.TargetRelations;
import org.bukkit.GameMode;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Quem o Arqueiro pode marcar ou ver como inimigo: jogador logado que não é do clan, boneco de teste inimigo ou
 * mob hostil (mesma regra do Combatente).
 */
public final class ArcherTargets {

    private final TargetRelations relations;
    private final AuthManager auth;

    public ArcherTargets(TargetRelations relations, AuthManager auth) {
        this.relations = relations;
        this.auth = auth;
    }

    public boolean isEnemy(Player self, Entity entity) {
        if (entity.equals(self) || !(entity instanceof LivingEntity living) || entity instanceof ArmorStand
                || living.isDead() || !living.isValid() || !relations.isEnemy(self, living)) {
            return false;
        }
        if (entity instanceof Player player) {
            return auth.isLoggedIn(player.getUniqueId()) && player.getGameMode() != GameMode.SPECTATOR;
        }
        return relations.isDummy(entity) || entity instanceof Enemy;
    }
}
