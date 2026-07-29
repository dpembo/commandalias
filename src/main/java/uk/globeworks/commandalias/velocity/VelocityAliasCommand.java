package uk.globeworks.commandalias.velocity;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Forwards proxy command execution to another proxy command. Mirrors the
 * Paper-side DynamicAliasCommand: hasPermission() always returns true, and
 * the real permission check happens naturally when executeAsync() re-runs
 * the target command through the proxy's normal command handling.
 */
public class VelocityAliasCommand implements SimpleCommand {

    private final String targetLabel;
    private final ProxyServer server;

    public VelocityAliasCommand(String targetLabel, ProxyServer server) {
        this.targetLabel = targetLabel;
        this.server = server;
    }

    @Override
    public void execute(Invocation invocation) {
        server.getCommandManager().executeAsync(invocation.source(), buildFullCommand(invocation.arguments()));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String full = buildFullCommand(invocation.arguments());
        try {
            return server.getCommandManager().offerSuggestions(invocation.source(), full).get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            return Collections.emptyList();
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return true;
    }

    private String buildFullCommand(String[] args) {
        return targetLabel + (args.length > 0 ? " " + String.join(" ", args) : "");
    }
}
