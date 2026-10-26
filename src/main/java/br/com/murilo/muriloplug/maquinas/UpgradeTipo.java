package br.com.murilo.muriloplug.maquinas;

import org.bukkit.Material;

import java.util.Locale;

/**
 * Tipos de upgrade das máquinas. Aqui ficam só a chave, o nome e o material do item (aparência); nível máximo, valor
 * por nível e onde é permitido vêm da {@link MaquinasConfig}.
 */
public enum UpgradeTipo {
    ESTOQUE("Estoque", Material.BRICK, false),
    VELOCIDADE("Velocidade", Material.SUGAR, true),
    EFICIENCIA("Eficiência", Material.AMETHYST_SHARD, true),
    FORTUNA("Fortuna", Material.RABBIT_FOOT, true),
    SORTE("Sorte", Material.PRISMARINE_CRYSTALS, true),
    BLINDAGEM("Blindagem", Material.IRON_INGOT, false),
    COLETA_AUTOMATICA("Coleta Automática", Material.FLINT, false),
    VENDA_AUTOMATICA("Venda Automática", Material.GOLD_NUGGET, false);

    private final String nome;
    private final Material material;
    private final boolean porcentagem;

    UpgradeTipo(String nome, Material material, boolean porcentagem) {
        this.nome = nome;
        this.material = material;
        this.porcentagem = porcentagem;
    }

    public String nome() {
        return nome;
    }

    public Material material() {
        return material;
    }

    /** Se o valor por nível é uma fração (mostrada em %). */
    public boolean porcentagem() {
        return porcentagem;
    }

    /** Tipo pelo nome (sem diferenciar maiúsculas), ou null. */
    public static UpgradeTipo parse(String text) {
        if (text == null) {
            return null;
        }
        try {
            return valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 1 -> I, 4 -> IV... (até 3999). */
    public static String romano(int numero) {
        int[] valores = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] letras = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder texto = new StringBuilder();
        int resto = Math.max(1, Math.min(3999, numero));
        for (int i = 0; i < valores.length; i++) {
            while (resto >= valores[i]) {
                texto.append(letras[i]);
                resto -= valores[i];
            }
        }
        return texto.toString();
    }
}
