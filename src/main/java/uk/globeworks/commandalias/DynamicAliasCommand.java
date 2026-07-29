package uk.globeworks.commandalias;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * A synthetic command that forwards execution to another registered command.
 *
 * Deliberately does NOT enforce its own permission node. Instead, execute()
 * calls Bukkit.dispatchCommand(), which re-runs full command resolution -
 * including whatever permission check the TARGET command already performs.
 * That means the alias is only ever as permissive as the command it points
 * to, and stays correct even if the target's permission changes later.
 *
 * The target can be a plain command ("ahouse") or a command plus fixed
 * arguments ("lp help") to alias a specific subcommand, e.g. "lphelp: lp
 * help" lets /lphelp run /lp help, with anything typed after /lphelp
 * appended on top of "help".
 */
public class DynamicAliasCommand extends Command {

    private final String targetLabel;
    private final String targetCommandName;
    private final String[] targetFixedArgs;
    private final CommandMap commandMap;
    private final Logger logger;

    protected DynamicAliasCommand(String aliasName, String targetLabel, CommandMap commandMap, Logger logger) {
        super(aliasName);
        this.targetLabel = targetLabel;
        this.commandMap = commandMap;
        this.logger = logger;

        String[] parts = targetLabel.trim().split("\\s+", 2);
        this.targetCommandName = parts[0];
        this.targetFixedArgs = (parts.length > 1) ? parts[1].split("\\s+") : new String[0];

        Command target = commandMap.getCommand(targetCommandName);
        if (target != null) {
            // Cosmetic only (help lists / some permission-aware UIs read this) -
            // actual enforcement happens in execute() via dispatchCommand.
            setPermission(target.getPermission());
            setPermissionMessage(target.getPermissionMessage());
            setDescription("Alias for /" + targetLabel);
        } else {
            setDescription("Alias for /" + targetLabel + " (target not currently registered - " +
                    "is the owning plugin loaded and did it load before CommandAlias?)");
        }
    }

    String getTargetLabel() {
        return targetLabel;
    }

    boolean isTargetResolved() {
        return commandMap.getCommand(targetCommandName) != null;
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        String full = targetLabel + (args.length > 0 ? " " + String.join(" ", args) : "");
        boolean handled = Bukkit.dispatchCommand(sender, full);
        if (!handled) {
            logger.warning("Alias '/" + commandLabel + "' -> '/" + targetLabel + "' did not resolve to a " +
                    "known command. Check the target name in config.yml and that its plugin is loaded.");
        }
        return handled;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        Command target = commandMap.getCommand(targetCommandName);
        if (target == null) {
            return Collections.emptyList();
        }
        try {
            // Prepend any fixed subcommand args (e.g. "help" for "lp help")
            // so tab-complete continues from the right point in the target
            // command's own argument tree.
            List<String> merged = new ArrayList<>(targetFixedArgs.length + args.length);
            Collections.addAll(merged, targetFixedArgs);
            Collections.addAll(merged, args);
            return target.tabComplete(sender, targetCommandName, merged.toArray(new String[0]));
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
