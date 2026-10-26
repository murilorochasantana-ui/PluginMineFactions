package br.com.murilo.muriloplug.maquinas;

/** Um upgrade (tipo + nível), instalado numa máquina ou guardado num item. */
public record Upgrade(UpgradeTipo tipo, int nivel) {

    public Upgrade {
        nivel = Math.max(1, nivel);
    }

    /** "ESTOQUE:2" (formato do arquivo e do item). */
    public String serialize() {
        return tipo.name() + ":" + nivel;
    }

    /** Lê "TIPO:nível", ou null se for inválido. */
    public static Upgrade parse(String text) {
        if (text == null) {
            return null;
        }
        String[] partes = text.split(":");
        UpgradeTipo tipo = UpgradeTipo.parse(partes[0]);
        if (tipo == null || partes.length != 2) {
            return null;
        }
        try {
            return new Upgrade(tipo, Integer.parseInt(partes[1].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** "§bEstoque II". */
    public String nomeColorido() {
        return "§b" + tipo.nome() + " " + UpgradeTipo.romano(nivel);
    }
}
