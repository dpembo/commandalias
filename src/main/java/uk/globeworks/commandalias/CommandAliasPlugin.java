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
import java.util.List;
import java.util.logging.Level;

public class CommandAliasPlugin extends JavaPlugin implements CommandExecutor {

    private final List<DynamicAliasCommand> registered = new ArrayList<>();
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
        if (registered.isEmpty()) {
            sender.sendMessage("§eCommandAlias: no aliases are currently registered.");
            return;
        }

        sender.sendMessage("§6§lCommandAlias §7- active aliases:");
        for (DynamicAliasCommand cmd : registered) {
            String note = cmd.isTargetResolved() ? "" : " §c(target not currently loaded)";
            sender.sendMessage("  §a/" + cmd.getLabel() + " §7-> §a/" + cmd.getTargetLabel() + note);
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

        for (String rawAlias : section.getKeys(false)) {
            String target = section.getString(rawAlias);
            String aliasName = rawAlias.toLowerCase().trim();

            if (target == null || target.isBlank()) {
                getLogger().warning("Skipping alias '" + aliasName + "': no target command configured.");
                continue;
            }
            target = target.trim();

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
        }
        registered.clear();
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
