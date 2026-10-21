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
            "§7Usa mana para lançar magias.",
            "§cFraqueza: leva +1.5 de dano por golpe."),
    COMBATENTE("Combatente", "§c", Material.IRON_SWORD, true,
            "§7Vida máxima de 13 corações.",
            "§7Recebe 30% menos dano.",
            "§7Grito de Fúria: ataca 1.5x mais rápido",
            "§7por 8s (recarga 30s), mas fica lento.",
            "§cFraqueza: não pode usar arco."),
    ARQUEIRO("Arqueiro", "§a", Material.BOW, true,
            "§7Alterna entre 1 flecha e rajada de 3.",
            "§7Mais velocidade com o arco na mão.",
            "§7Pode usar a Flecha Explosiva.",
            "§cFraqueza: menos dano com espadas e machados."),
    ENGENHEIRO("Engenheiro", "§6", Material.TNT, true,
            "§7Guarda construções em Livros de Estrutura",
            "§7e coloca elas prontas no mundo depois.",
            "§7Ex: canhões de TNT."),
    SUPORTE("Suporte", "§b", Material.GOLDEN_APPLE, true,
            "§7Vincula-se a um aliado e divide o dano.",
            "§7Prende inimigos, cura e acelera o clan.",
            "§7Voa por alguns segundos com a pena.",
            "§cFraqueza: vida máxima de 8 corações.");

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
