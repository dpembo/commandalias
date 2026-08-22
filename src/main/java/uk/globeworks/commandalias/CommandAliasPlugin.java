package uk.globeworks.commandalias;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class CommandAliasPlugin extends JavaPlugin implements CommandExecutor {

    private final List<DynamicAliasCommand> registered = new ArrayList<>();
    private final List<MultiSubAliasCommand> registeredMulti = new ArrayList<>();
    private CommandMap commandMap;

    @Override
    public void onEnable() {
        saveDefaultConfig();

       String logo =""
                + "          _____\n" 
                + "         |A .  | _____\n"
                + "Command  | /.\\ ||A ^  | _____\n"
                + "Alias    |(_._)|| / \\ ||A _  | _____\n"
                + "         |  |  || \\ / || ( ) ||A_ _ |\n"
                + "         |____V||  .  ||(_'_)||( v )|\n"
                + "(c)2026         |____V||  |  || \\ / |\n"
                + "pembo.co.uk            |____V||  .  |\n"
                + "                              |____V\n|";

        getLogger().info("\n" + logo);
        getLogger().info("CommandAlias version " + getDescription().getVersion() + "");        
        // "commandalias" itself is a normal plugin.yml command (for reload).
        var self = getCommand("commandalias");
        if (self != null) {
            self.setExecutor(this);
        }

        commandMap = resolveCommandMap();
        if (commandMap == null) {
            getLogger().severe("Could not access the server CommandMap via reflection - " +
                    "aliasing disabled. This usually means a server API change; check for a plugin update.");
            return;
        }

        registerAliases();
    }

    @Override
    public void onDisable() {
        unregisterAliases();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("commandalias.admin")) {
                sender.sendMessage("§cYou don't have permission to do that.");
                return true;
            }
            unregisterAliases();
            reloadConfig();
            registerAliases();
            sender.sendMessage("§aCommandAlias: aliases reloaded (" + registered.size() + " active).");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        sender.sendMessage("§eUnknown subcommand. Usage: /commandalias <help|reload>");
        return true;
    }

    private void sendHelp(CommandSender sender) {
        int total = registered.size() + registeredMulti.stream().mapToInt(c -> c.getSubAliases().size()).sum();
        if (total == 0) {
            sender.sendMessage("§eCommandAlias: no aliases are currently registered.");
            return;
        }

        sender.sendMessage("§6§lCommandAlias §7- active aliases:");
        for (DynamicAliasCommand cmd : registered) {
            String note = cmd.isTargetResolved() ? "" : " §c(target not currently loaded)";
            sender.sendMessage("  §a/" + cmd.getLabel() + " §7-> §a/" + cmd.getTargetLabel() + note);
        }
        for (MultiSubAliasCommand cmd : registeredMulti) {
            for (Map.Entry<String, String> entry : cmd.getSubAliases().entrySet()) {
                sender.sendMessage("  §a/" + cmd.getLabel() + " " + entry.getKey() + " §7-> §a/" + entry.getValue());
            }
        }
    }

    private void registerAliases() {
        if (commandMap == null) {
            return;
        }

        ConfigurationSection section = getConfig().getConfigurationSection("aliases");
        if (section == null) {
            getLogger().warning("No 'aliases' section found in config.yml - nothing to register.");
            return;
        }

        // Separate single-word aliases from multi-word (space-containing) aliases
        Map<String, String> singleAliases = new LinkedHashMap<>();
        Map<String, Map<String, String>> multiAliases = new LinkedHashMap<>();

        for (String rawAlias : section.getKeys(false)) {
            String target = section.getString(rawAlias);
            String aliasName = rawAlias.toLowerCase().trim();

            if (target == null || target.isBlank()) {
                getLogger().warning("Skipping alias '" + aliasName + "': no target command configured.");
                continue;
            }
            target = target.trim();

            if (aliasName.contains(" ")) {
                String[] parts = aliasName.split("\\s+", 2);
                multiAliases.computeIfAbsent(parts[0], k -> new LinkedHashMap<>()).put(parts[1], target);
            } else {
                singleAliases.put(aliasName, target);
            }
        }

        // Remove any multi-alias group whose first word conflicts with a simple alias
        Iterator<String> iter = multiAliases.keySet().iterator();
        while (iter.hasNext()) {
            String firstWord = iter.next();
            if (singleAliases.containsKey(firstWord)) {
                getLogger().warning("Skipping multi-sub-aliases for '" + firstWord + "': conflicts with a " +
                        "simple alias of the same name. Rename one of them.");
                iter.remove();
            }
        }

        // Register single-word aliases
        for (Map.Entry<String, String> entry : singleAliases.entrySet()) {
            String aliasName = entry.getKey();
            String target = entry.getValue();
            if (commandMap.getCommand(aliasName) != null) {
                getLogger().warning("Alias '" + aliasName + "' was NOT registered: a command with that " +
                        "name/label already exists (possibly from another plugin). Pick a different alias name.");
                continue;
            }
            DynamicAliasCommand aliasCommand = new DynamicAliasCommand(aliasName, target, commandMap, getLogger());
            commandMap.register(getName().toLowerCase(), aliasCommand);
            registered.add(aliasCommand);
            getLogger().info("Registered alias '/" + aliasName + "' -> '/" + target + "'");
        }

        // Register multi-word alias groups
        for (Map.Entry<String, Map<String, String>> entry : multiAliases.entrySet()) {
            String firstWord = entry.getKey();
            if (commandMap.getCommand(firstWord) != null) {
                getLogger().warning("Multi-alias '" + firstWord + "' was NOT registered: a command with that " +
                        "name/label already exists (possibly from another plugin). Pick a different alias name.");
                continue;
            }
            MultiSubAliasCommand multiCmd = new MultiSubAliasCommand(firstWord, entry.getValue(), commandMap, getLogger());
            commandMap.register(getName().toLowerCase(), multiCmd);
            registeredMulti.add(multiCmd);
            for (String subKey : entry.getValue().keySet()) {
                getLogger().info("Registered multi-alias '/" + firstWord + " " + subKey + "' -> '/" + entry.getValue().get(subKey) + "'");
            }
        }

        // Registering into the CommandMap doesn't push an updated command
        // tree to clients who are already connected - the client only gets
        // that on join, or when explicitly told to refresh. Without this,
        // the alias works fine when typed but won't appear in the "/"
        // autocomplete dropdown for anyone already online.
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.updateCommands();
        }
    }

    private void unregisterAliases() {
        if (commandMap != null) {
            for (DynamicAliasCommand cmd : registered) {
                cmd.unregister(commandMap);
            }
            for (MultiSubAliasCommand cmd : registeredMulti) {
                cmd.unregister(commandMap);
            }
        }
        registered.clear();
        registeredMulti.clear();
    }

    /**
     * Paper/Spigot don't expose a public API to register arbitrary commands
     * at runtime, so we grab the private CommandMap field off CraftServer.
     * This is the same trick used by things like command-graph plugins and
     * has been stable across Bukkit/Paper for a very long time, but if a
     * future server version renames the field this will start logging the
     * severe error in onEnable() instead of silently failing.
     */
    private CommandMap resolveCommandMap() {
        try {
            Field field = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            field.setAccessible(true);
            return (CommandMap) field.get(Bukkit.getServer());
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to resolve CommandMap via reflection", e);
            return null;
        }
    }
}
