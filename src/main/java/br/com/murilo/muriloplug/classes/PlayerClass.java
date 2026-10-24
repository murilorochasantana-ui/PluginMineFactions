package br.com.murilo.muriloplug.classes;

import org.bukkit.Material;

import java.util.List;

/**
 * Classes do jogador. As descrições (lore do menu) ficam aqui, num lugar só, para editar fácil.
 * Para liberar uma classe nova, troque o {@code available} dela para true.
 */
public enum PlayerClass {

    MAGO("Mago", "§d", Material.BLAZE_ROD, true,
            "§7Usa staffs mágicas de fogo, gelo,",
            "§7raio, teleporte e vento.",
            "§7Clique esquerdo: Míssil Arcano (devolve mana).",
            "§7Clique direito: lança a magia.",
            "§7Passivas: Poder Arcano e Escudo de Mana.",
            "§cFraqueza: +15% de dano recebido e",
            "§c60% menos dano com armas."),
    COMBATENTE("Combatente", "§c", Material.IRON_SWORD, true,
            "§7Vida máxima de 13 corações.",
            "§7Recebe 20% menos dano (não vale para magia e fogo).",
            "§7Acumula Fúria lutando (barra de XP).",
            "§7Berserker, Investida, Provocar, Golpe Pulando.",
            "§7Passivas: Inabalável e Último Suspiro.",
            "§cFraqueza: sem arco, besta e tridente;",
            "§c+30% de dano das magias do Mago."),
    ARQUEIRO("Arqueiro", "§a", Material.BOW, true,
            "§7Foco (barra de XP): +5 a cada 2s fora de combate,",
            "§7+2 por flecha que acerta. Mais dano de longe e",
            "§7tiro na cabeça (+50%). Flecha Explosiva.",
            "§7[F] Rajada de 3 (gasta 1 flecha) | [Shift+F] Dash (20)",
            "§7Pulo Duplo no ar (10) | Chuva de Flechas (40):",
            "§7Shift+Clique Esq | Marca do Caçador (60): Clique Esq",
            "§7Ultra Foco (100): Q, segure o arco 2s e solte.",
            "§7Passivas: Recuperação (30% de poupar flecha),",
            "§7Pés Leves (queda) e Olhos de Águia (agachado).",
            "§cFraquezas: 40% menos dano com espada e machado;",
            "§c+15% de dano corpo a corpo recebido."),
    ENGENHEIRO("Engenheiro", "§6", Material.TNT, true,
            "§7Guarda construções em Livros de Estrutura",
            "§7e coloca elas prontas no mundo depois.",
            "§7Ex: canhões de TNT. A estrutura sobe aos poucos.",
            "§7Energia (barra de XP): +2 por segundo.",
            "§7Passivas: Especialista em Explosivos (50% menos",
            "§7dano de explosões), Mãos Rápidas (Pressa com",
            "§7picareta) e Reparo (Shift+clique, 80 de energia).",
            "§7Espada -30%, arco -20%, besta +30% de dano.",
            "§cFraqueza: armadura de netherite deixa lento;",
            "§carmadura de ferro dá choque nas suas ações."),
    SUPORTE("Suporte", "§b", Material.GOLDEN_APPLE, true,
            "§7Vida máxima de 18 corações.",
            "§7Harmonia (barra de XP): sobe curando, pelo",
            "§7vínculo, por assistência e com o tempo.",
            "§7Vínculo: o aliado leva 10% menos dano e você",
            "§7divide 35% do dano dele. Elo Drenante e",
            "§7Troca Equivalente (salva o vinculado da morte).",
            "§7Prende, cura, acelera, voa e troca de lugar.",
            "§7Escudo Sagrado, Purificar, Totem de Cura,",
            "§7Silêncio e Maldição (Shift + clique nos itens).",
            "§7Passivas: Socorrista e Recompensa. /visao",
            "§cFraqueza: 40% menos dano com armas.");

    private final String displayName;
    private final String color;
    private final Material icon;
    private final boolean available;
    private final List<String> description;

    PlayerClass(String displayName, String color, Material icon, boolean available, String... description) {
        this.displayName = displayName;
        this.color = color;
        this.icon = icon;
        this.available = available;
        this.description = List.of(description);
    }

    /** Nome em português, sem cor. */
    public String displayName() {
        return displayName;
    }

    /** Código de cor legado (ex: "§d"). */
    public String color() {
        return color;
    }

    /** Nome já colorido. */
    public String coloredName() {
        return color + displayName;
    }

    public Material icon() {
        return icon;
    }

    /** Se a classe já pode ser escolhida. */
    public boolean available() {
        return available;
    }

    public List<String> description() {
        return description;
    }
}
