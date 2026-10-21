# MinePlug

Plugin de Minecraft (Paper 1.21.x), Java 21, Maven.

- Pacote base: `br.com.murilo.muriloplug` (classe principal `MinePlug`, declarada em `src/main/resources/plugin.yml`).
- API: `paper-api` 1.21.4 (scope `provided`). Para outra versão, altere no `pom.xml` e o `api-version` no `plugin.yml`.
- Build: `mvn clean package` gera o jar em `target/`. O Maven vem embutido no IntelliJ (não há `mvn` no PATH).
- `plugin.yml` usa filtragem de recursos (`${project.version}`).
- Novos comandos/permissões devem ser registrados no `plugin.yml`.
