package uk.globeworks.commandalias;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;

import java.util.*;
import java.util.logging.Logger;

/**
 * Synthetic command that routes to different targets based on sub-command
 * words. Registered once per first-word group; e.g. all "warp *" entries
 * share one MultiSubAliasCommand registered as "warp".
 *
 * Permission model matches DynamicAliasCommand: no own node is enforced.
 * execute() calls Bukkit.dispatchCommand(), which re-runs the target's own
 * permission check.
 */
public class MultiSubAliasCommand extends Command {

    // Insertion-order map: lower-case space-joined subkey -> target command string
    private final Map<String, String> subAliases;
    private final CommandMap commandMap;
    private final Logger logger;

    protected MultiSubAliasCommand(String commandName, Map<String, String> subAliases,
                                   CommandMap commandMap, Logger logger) {
        super(commandName);
        this.subAliases = Collections.unmodifiableMap(new LinkedHashMap<>(subAliases));
        this.commandMap = commandMap;
        this.logger = logger;
        setDescription("Multi-sub-alias with " + subAliases.size() + " sub-command(s)");
    }

    Map<String, String> getSubAliases() {
        return subAliases;
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        // Find the longest subkey whose words match the start of args
        String matchedKey = null;
        String matchedTarget = null;
        int matchedLength = 0;

        for (Map.Entry<String, String> entry : subAliases.entrySet()) {
            String[] subParts = entry.getKey().split(" ");
            if (args.length >= subParts.length && subParts.length > matchedLength) {
                boolean match = true;
                for (int i = 0; i < subParts.length; i++) {
                    if (!args[i].equalsIgnoreCase(subParts[i])) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    matchedKey = entry.getKey();
                    matchedTarget = entry.getValue();
                    matchedLength = subParts.length;
                }
            }
        }

        if (matchedTarget == null) {
            sender.sendMessage("§eUsage: /" + commandLabel + " <" + String.join("|", subAliases.keySet()) + ">");
            return true;
        }

        String[] remaining = Arrays.copyOfRange(args, matchedLength, args.length);
        String full = matchedTarget + (remaining.length > 0 ? " " + String.join(" ", remaining) : "");
        boolean handled = Bukkit.dispatchCommand(sender, full);
        if (!handled) {
            logger.warning("Multi-alias '/" + commandLabel + " " + matchedKey + "' -> '/" + full
                    + "' did not resolve. Check the target name in config.yml.");
        }
        return handled;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (args.length == 0) return Collections.emptyList();

        String lastArg = args[args.length - 1].toLowerCase();
        String[] confirmedParts = args.length > 1
                ? Arrays.copyOfRange(args, 0, args.length - 1)
                : new String[0];

        // If already-confirmed args spell out (or extend beyond) a subkey, delegate to target
        for (Map.Entry<String, String> entry : subAliases.entrySet()) {
            String[] skParts = entry.getKey().toLowerCase().split(" ");
            if (confirmedParts.length >= skParts.length) {
                boolean match = true;
                for (int i = 0; i < skParts.length; i++) {
                    if (!confirmedParts[i].equalsIgnoreCase(skParts[i])) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    String[] extra = Arrays.copyOfRange(confirmedParts, skParts.length, confirmedParts.length);
                    String[] delegateArgs = Arrays.copyOf(extra, extra.length + 1);
                    delegateArgs[extra.length] = lastArg;
                    return delegateTabComplete(sender, entry.getValue(), delegateArgs);
                }
            }
        }

        // Suggest the next word of any subkey that starts with the confirmed prefix
        String confirmedStr = String.join(" ", confirmedParts).toLowerCase();
        Set<String> suggestions = new LinkedHashSet<>();
        for (String subKey : subAliases.keySet()) {
            String sk = subKey.toLowerCase();
            if (confirmedStr.isEmpty() || sk.startsWith(confirmedStr + " ")) {
                String rest = confirmedStr.isEmpty() ? sk : sk.substring(confirmedStr.length() + 1);
                String nextWord = rest.split(" ", 2)[0];
                if (nextWord.startsWith(lastArg)) {
                    suggestions.add(nextWord);
                }
            }
        }
        return new ArrayList<>(suggestions);
    }

    private List<String> delegateTabComplete(CommandSender sender, String targetStr, String[] delegateArgs) {
        String[] parts = targetStr.split("\\s+", 2);
        String targetCmdName = parts[0];
        String[] fixedArgs = parts.length > 1 ? parts[1].split("\\s+") : new String[0];
        Command targetCmd = commandMap.getCommand(targetCmdName);
        if (targetCmd == null) return Collections.emptyList();

        List<String> merged = new ArrayList<>(fixedArgs.length + delegateArgs.length);
        Collections.addAll(merged, fixedArgs);
        Collections.addAll(merged, delegateArgs);
        try {
            return targetCmd.tabComplete(sender, targetCmdName, merged.toArray(new String[0]));
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
