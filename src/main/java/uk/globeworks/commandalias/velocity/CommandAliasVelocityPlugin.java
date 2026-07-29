package uk.globeworks.commandalias.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Proxy-side counterpart to the Paper CommandAliasPlugin. Same descriptor
 * file (velocity-plugin.json) is bundled in the same jar as plugin.yml -
 * Velocity loads this class and ignores plugin.yml, Paper loads
 * uk.globeworks.commandalias.CommandAliasPlugin and ignores
 * velocity-plugin.json. One jar, drop it on the proxy and on every backend
 * server; each side only sees its own half.
 */
@Plugin(id = "commandalias", name = "CommandAlias", version = "1.0.0", authors = {"dpembo"})
public class CommandAliasVelocityPlugin {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final List<String> registeredAliases = new ArrayList<>();

    @Inject
    public CommandAliasVelocityPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        registerAliases();
    }

    private void registerAliases() {
        Properties config = loadOrCreateConfig();
        if (config == null) {
            return;
        }

        CommandManager commandManager = server.getCommandManager();

        for (String rawAlias : config.stringPropertyNames()) {
            String target = config.getProperty(rawAlias);
            String aliasName = rawAlias.toLowerCase().trim();

            if (target == null || target.isBlank()) {
                logger.warn("Skipping alias '{}': no target command configured.", aliasName);
                continue;
            }
            target = target.trim();

            if (commandManager.hasCommand(aliasName)) {
                logger.warn("Alias '{}' was NOT registered: a command with that name already exists.", aliasName);
                continue;
            }

            CommandMeta meta = commandManager.metaBuilder(aliasName).plugin(this).build();
            commandManager.register(meta, new VelocityAliasCommand(target, server));
            registeredAliases.add(aliasName);
            logger.info("Registered proxy alias '/{}' -> '/{}'", aliasName, target);
        }
    }

    private Properties loadOrCreateConfig() {
        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }
            Path configPath = dataDirectory.resolve("aliases.properties");
            if (!Files.exists(configPath)) {
                try (InputStream in = getClass().getResourceAsStream("/aliases.properties")) {
                    if (in != null) {
                        Files.copy(in, configPath);
                    } else {
                        Files.createFile(configPath);
                    }
                }
            }
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(configPath)) {
                props.load(in);
            }
            return props;
        } catch (IOException e) {
            logger.error("Failed to load aliases.properties", e);
            return null;
        }
    }
}
