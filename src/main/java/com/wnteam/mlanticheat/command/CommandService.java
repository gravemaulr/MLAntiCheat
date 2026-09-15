package com.wnteam.mlanticheat.command;

import com.wnteam.mlanticheat.MLAntiCheat;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CommandService {

    private final MLAntiCheat plugin;
    private final MLACCommand handler;
    private final List<String> registered = new ArrayList<>();
    private final String prefix;

    public CommandService(MLAntiCheat plugin, MLACCommand handler) {
        this.plugin = plugin;
        this.handler = handler;
        this.prefix = plugin.getName().toLowerCase(Locale.ROOT);
    }

    public void register() {
        CommandMap map = Bukkit.getCommandMap();
        unregister(map);
        String name = plugin.getSettings().commandName;
        List<String> aliases = plugin.getSettings().commandAliases;
        Command command = new Command(name, "MLAntiCheat control", "/" + name + " help", aliases) {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                return handler.onCommand(sender, this, label, args);
            }

            @Override
            public List<String> tabComplete(CommandSender sender, String label, String[] args) {
                List<String> completions = handler.onTabComplete(sender, this, label, args);
                return completions == null ? List.of() : completions;
            }
        };
        map.register(prefix, command);
        registered.add(name.toLowerCase(Locale.ROOT));
        for (String alias : aliases) {
            registered.add(alias.toLowerCase(Locale.ROOT));
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.updateCommands();
        }
    }

    public void shutdown() {
        unregister(Bukkit.getCommandMap());
    }

    private void unregister(CommandMap map) {
        if (!(map instanceof SimpleCommandMap simple)) {
            registered.clear();
            return;
        }
        Map<String, Command> known = simple.getKnownCommands();
        for (String label : registered) {
            Command removed = known.remove(label);
            known.remove(prefix + ":" + label);
            if (removed != null) {
                removed.unregister(simple);
            }
        }
        registered.clear();
    }
}
