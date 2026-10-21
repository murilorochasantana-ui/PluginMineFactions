package br.com.murilo.muriloplug.auth;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.AbstractFilter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Filtro do Log4j no logger raiz que esconde a senha nas linhas "issued server command: /login ...".
 * A linha original é barrada e uma versão sem a senha ("... /login ****") é registrada no lugar.
 */
public final class CommandPasswordFilter extends AbstractFilter {

    private static final Pattern PASSWORD_COMMAND = Pattern.compile(
            "(issued server command: /(?:login|logar|register|registrar|trocarsenha))(?=\\s|$)",
            Pattern.CASE_INSENSITIVE);

    /** Evita que a linha já limpa (que registramos aqui mesmo) seja filtrada de novo. */
    private static final ThreadLocal<Boolean> REWRITING = ThreadLocal.withInitial(() -> false);

    private static CommandPasswordFilter installed;

    private CommandPasswordFilter() {
        super(Filter.Result.NEUTRAL, Filter.Result.NEUTRAL);
    }

    /** Instala o filtro no logger raiz. Retorna false se não foi possível. */
    public static synchronized boolean install() {
        try {
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            LoggerConfig root = context.getConfiguration().getRootLogger();
            CommandPasswordFilter filter = new CommandPasswordFilter();
            filter.start();
            root.addFilter(filter);
            context.updateLoggers();
            installed = filter;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static synchronized void uninstall() {
        if (installed == null) {
            return;
        }
        try {
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            context.getConfiguration().getRootLogger().removeFilter(installed);
            context.updateLoggers();
        } catch (Throwable ignored) {
            // Ao desligar, não há mais o que fazer se o log4j não estiver disponível.
        }
        installed = null;
    }

    @Override
    public Result filter(LogEvent event) {
        if (REWRITING.get() || event.getMessage() == null) {
            return Result.NEUTRAL;
        }
        String message = event.getMessage().getFormattedMessage();
        if (message == null) {
            return Result.NEUTRAL;
        }
        Matcher matcher = PASSWORD_COMMAND.matcher(message);
        if (!matcher.find()) {
            return Result.NEUTRAL;
        }

        String sanitized = message.substring(0, matcher.end()) + " ****";
        REWRITING.set(true);
        try {
            Level level = event.getLevel();
            LogManager.getLogger(event.getLoggerName()).log(level, "{}", sanitized);
        } finally {
            REWRITING.set(false);
        }
        return Result.DENY;
    }
}
