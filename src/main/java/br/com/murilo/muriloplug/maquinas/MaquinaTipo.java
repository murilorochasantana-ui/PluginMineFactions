package br.com.murilo.muriloplug.maquinas;

import java.util.Locale;

/**
 * Os tipos de máquina. Aqui fica só a chave (o nome da seção em maquinas.yml): nome, bloco, preço, tabela de produção
 * e todos os outros números vêm da {@link MaquinasConfig}.
 */
public enum MaquinaTipo {
    MINERADOR,
    FAZENDA,
    OBSIDIANA,
    DROPS;

    /** Tipo pelo nome (sem diferenciar maiúsculas), ou null. */
    public static MaquinaTipo parse(String text) {
        if (text == null) {
            return null;
        }
        try {
            return valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
