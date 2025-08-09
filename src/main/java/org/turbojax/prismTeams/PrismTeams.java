package org.turbojax.prismTeams;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.math.BlockPosition;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlRepresenter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.Yaml;

public final class PrismTeams extends JavaPlugin implements Listener {
    public List<String> teams = new ArrayList<>();
    public HashMap<String, List<String>> teamMembers = new HashMap<>();
    public HashMap<String, Location> teamWarps = new HashMap<>();
    public List<String> frozen = new ArrayList<>();
    public HashMap<String, String> playerToTeamName = new HashMap<>();

    public final MiniMessage serializer = MiniMessage.miniMessage();
    public final FileConfiguration config = getConfig();

    @Override
    public void onEnable() {
        // Loading configs
        loadConfig();

        // Registering event listeners
        getServer().getPluginManager().registerEvents(this, this);

        // Setting up the commands
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("prismteams");

        // Creates a team
        root.then(Commands.literal("add")
                .then(Commands.argument("teamName", StringArgumentType.string())
                        .executes(ctx -> {
                            String teamName = ctx.getArgument("teamName", String.class);
                            CommandSender sender = ctx.getSource().getSender();

                            // Cancel if team already exists
                            if (teams.contains(teamName)) {
                                sendMessage(sender, "<red>Team " + teamName + " already exists");
                                return 1;
                            }

                            // Add the team and send output
                            teams.add(teamName);
                            sendMessage(sender, "<green>Created new team " + teamName);

                            writeConfig();
                            return 1;
                        })
                )
        );

        // Lists the existing teams
        root.then(Commands.literal("list")
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();

                    sendMessage(sender, "<#FFA500>Listing teams:");
                    for (String team : teams) {
                        sendMessage(sender, "<#FFA500>Team<#555555> » <reset>" + team);
                    }

                    return 1;
                })
        );

        // Deletes a team
        root.then(Commands.literal("delete")
                .then(Commands.argument("teamName", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            for (String team : teams) builder.suggest(team);

                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            String teamName = ctx.getArgument("teamName", String.class);

                            // Cancels if the team doesn't exist.
                            if (!teams.contains(teamName)) {
                                sendMessage(sender, "<red>Team " + teamName + " does not exist");
                                return 1;
                            }

                            // Deleting the team
                            teams.remove(teamName);
                            teamMembers.remove(teamName);
                            sendMessage(sender, "<green>Deleted team " + teamName);

                            writeConfig();
                            return 1;
                        })
                )
        );

        // Adds a user to a team
        root.then(Commands.literal("addmember")
                .then(Commands.argument("teamName", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            for (String team : teams) builder.suggest(team);

                            return builder.buildFuture();
                        })
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(ctx -> {
                                    CommandSender sender = ctx.getSource().getSender();
                                    String teamName = ctx.getArgument("teamName", String.class);
                                    Player player = ctx.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource()).getFirst();

                                    // Cancelling if the team doesn't exist
                                    if (!teams.contains(teamName)) {
                                        sendMessage(sender, "<red>Team " + teamName + " does not exist");
                                        return 1;
                                    }

                                    // Adding the player to the team
                                    teamMembers.get(teamName).add(player.getName());
                                    sendMessage(sender, "<green>Added " + player.getName() + " to team " + teamName);

                                    writeConfig();
                                    return 1;
                                })
                        )
                )
        );

        // Lists the users on a team
        root.then(Commands.literal("listmembers")
                .then(Commands.argument("teamName", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            for (String team : teams) builder.suggest(team);

                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            String teamName = ctx.getArgument("teamName", String.class);

                            // Verify that the team exists
                            if (!teams.contains(teamName)) {
                                sendMessage(sender, "<red>Team " + teamName + " does not exist");
                                return 1;
                            }

                            // List the members
                            sendMessage(sender, "<#FFA500>Members of " + teamName + ":");
                            teamMembers.get(teamName).forEach(member -> {
                                sendMessage(sender, "<#FFA500>Member<#555555> » <reset>" + member);
                            });

                            return 1;
                        })
                )
        );

        // Removes a user from a team
        root.then(Commands.literal("delmember")
                .then(Commands.argument("teamName", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            for (String team : teams) builder.suggest(team);

                            return builder.buildFuture();
                        })
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(ctx -> {
                                    CommandSender sender = ctx.getSource().getSender();
                                    String teamName = ctx.getArgument("teamName", String.class);
                                    Player player = ctx.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource()).getFirst();

                                    // Cancels if the team doesn't exist.
                                    if (!teams.contains(teamName)) {
                                        sendMessage(sender, "<red>Team " + teamName + " does not exist");
                                        return 1;
                                    }

                                    // Cancels if the player isn't on the team.
                                    if (!teamMembers.get(teamName).contains(player.getName())) {
                                        sendMessage(sender, "<red>" + player.getName() + " is not on team " + teamName);
                                        return 1;
                                    }

                                    // Removing the player from the team.
                                    teamMembers.get(teamName).remove(player.getName());
                                    sendMessage(sender, "<green>Removed " + player.getName() + " from team " + teamName);

                                    writeConfig();
                                    return 1;
                                })
                        )
                )
        );

        // Sets the warp point to the coordinate parameters or the player's position
        root.then(Commands.literal("setwarp")
                .then(Commands.argument("teamName", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            for (String team : teams) builder.suggest(team);

                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            String teamName = ctx.getArgument("teamName", String.class);

                            // Cancels if the team doesn't exist.
                            if (!teams.contains(teamName)) {
                                sendMessage(sender, "<red>Team " + teamName + " does not exist");
                                return 1;
                            }

                            // Cancels if the console runs the command
                            if (sender instanceof ConsoleCommandSender) {
                                sendMessage(sender, "<red>Cannot run from console.");
                            }

                            // Sets the warp point
                            if (sender instanceof Player player) {
                                teamWarps.put(teamName, player.getLocation());
                                sendMessage(sender, "<green>Set the warp point for team " + teamName);
                            }

                            writeConfig();
                            return 1;
                        })
                        .then(Commands.argument("location", ArgumentTypes.blockPosition())
                                .executes(ctx -> {
                                    CommandSender sender = ctx.getSource().getSender();
                                    String teamName = ctx.getArgument("teamName", String.class);
                                    BlockPosition pos = ctx.getArgument("arg", BlockPositionResolver.class).resolve(ctx.getSource());

                                    // Cancels if the team doesn't exist.
                                    if (!teams.contains(teamName)) {
                                        sendMessage(sender, "<red>Team " + teamName + " does not exist");
                                        return 1;
                                    }

                                    // Cancels if the console runs the command
                                    if (sender instanceof ConsoleCommandSender) {
                                        sendMessage(sender, "<red>Cannot run from console.");
                                    }

                                    // Setting the warp point.
                                    teamWarps.put(teamName, pos.toLocation(((Player) sender).getWorld()));
                                    sendMessage(sender, "<green>Set the warp point for team " + teamName);

                                    writeConfig();
                                    return 1;
                                })
                        )
                )
        );

        // Gets the coordinates of the team's warp point
        root.then(Commands.literal("getwarp")
                .then(Commands.argument("teamName", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            for (String team : teams) builder.suggest(team);

                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            String teamName = ctx.getArgument("teamName", String.class);

                            // Cancels if the team doesn't exist
                            if (!teams.contains(teamName)) {
                                sendMessage(sender, "<red>Team " + teamName + " does not exist");
                                return 1;
                            }

                            // Cancels if a warp point has not been set.
                            if (teamWarps.get(teamName) == null) {
                                sendMessage(sender, "<#FFA500>Team " + teamName + " does not have a warp set");
                                return 1;
                            }

                            Location location = teamWarps.get(teamName);

                            // Sends the warp point to the sender
                            sendMessage(sender, String.format("<#FFA500>Warp point: (%.2f, %.2f, %.2f)", location.x(), location.y(), location.z()));

                            return 1;
                        })
                )
        );

        // Removes that team's warp point
        root.then(Commands.literal("delwarp")
                .then(Commands.argument("teamName", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            for (String team : teams) builder.suggest(team);

                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            String teamName = ctx.getArgument("teamName", String.class);

                            // Cancels if the team doesn't exist
                            if (!teams.contains(teamName)) {
                                sendMessage(sender, "<red>Team " + teamName + " does not exist");
                                return 1;
                            }

                            // Cancels if the warp doesn't exist
                            if (!teamWarps.containsKey(teamName)) {
                                sendMessage(sender, "<red>Team " + teamName + " does not have a warp set");
                                return 1;
                            }

                            // Removes the warp point
                            teamWarps.remove(teamName);
                            sendMessage(sender, "<green>Removed warp for team " + teamName);

                            writeConfig();
                            return 1;
                        })
                )
        );

        // Teleports all members or a single member of a team to the warp point or the
        // player if a warp point is not set.
        root.then(Commands.literal("warp")
                .then(Commands.literal("team")
                        .then(Commands.argument("teamName", StringArgumentType.string())
                                .suggests((ctx, builder) -> {
                                    for (String team : teams) builder.suggest(team);

                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    CommandSender sender = ctx.getSource().getSender();
                                    String teamName = ctx.getArgument("teamName", String.class);

                                    // Cancels if the team doesn't exist
                                    if (!teams.contains(teamName)) {
                                        sendMessage(sender, "<red>Team " + teamName + " does not exist");
                                        return 1;
                                    }

                                    // Looping over the members of the team
                                    for (String member : teamMembers.get(teamName)) {
                                        Player player = getServer().getPlayer(member);

                                        // Skipping players who are not found
                                        if (player == null) {
                                            sendMessage(sender, "<red>Cannot find " + member + ".  Are they offline?");
                                            continue;
                                        }

                                        // Teleporting the player to the warp point
                                        Location loc = teamWarps.get(teamName).clone();
                                        loc.setYaw(player.getYaw());
                                        loc.setPitch(player.getPitch());
                                        player.teleport(loc);
                                        sendMessage(sender, "<green>Teleported " + member);
                                    }

                                    writeConfig();
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("player")
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(ctx -> {
                                    CommandSender sender = ctx.getSource().getSender();
                                    Player player = ctx.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource()).getFirst();

                                    // Cancels if the player is not on a team
                                    if (!playerToTeamName.containsKey(player.getName())) {
                                        sendMessage(sender, "<red>" + player.getName() + " is not on a team");
                                        return 1;
                                    }

                                    // Teleporting the player to the warp point
                                    Location loc = teamWarps.get(playerToTeamName.get(player.getName())).clone();
                                    loc.setYaw(player.getYaw());
                                    loc.setPitch(player.getPitch());
                                    player.teleport(loc);
                                    sendMessage(sender, "<green>Teleported " + player.getName());

                                    writeConfig();
                                    return 1;
                                })
                        )
                )
        );

        // Freezes a player on a team or their whole team
        root.then(Commands.literal("freeze")
                .then(Commands.literal("team")
                        .then(Commands.argument("teamName", StringArgumentType.string())
                                .suggests((ctx, builder) -> {
                                    for (String team : teams) builder.suggest(team);

                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    CommandSender sender = ctx.getSource().getSender();
                                    String teamName = ctx.getArgument("teamName", String.class);

                                    // Cancels if the team doesn't exist
                                    if (!teams.contains(teamName)) {
                                        sendMessage(sender, "<red>Team " + teamName + " does not exist");
                                        return 1;
                                    }

                                    // Adding all the members on the team to the frozen list
                                    for (String member : teamMembers.get(teamName)) {
                                        Player player = getServer().getPlayer(member);

                                        // Skipping if the player is offline
                                        if (player == null) {
                                            sendMessage(sender, "<red>Cannot find " + member + ".  Are they online?");
                                            continue;
                                        }

                                        // Skipping if the player is already frozen
                                        if (frozen.contains(member)) {
                                            sendMessage(sender, "<red>" + member + " is already frozen");
                                            continue;
                                        }

                                        // Freezing the player
                                        frozen.add(member);
                                        player.lockFreezeTicks(true);
                                        player.setFreezeTicks(139);
                                        sendMessage(player, "<aqua>You are frozen!  You cannot move until a moderator unfreezes you.");
                                        sendMessage(sender, "<green>Froze " + member);
                                    }

                                    writeConfig();
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("player")
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(ctx -> {
                                    CommandSender sender = ctx.getSource().getSender();
                                    Player player = ctx.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource()).getFirst();

                                    // Skipping if the player is offline
                                    if (player == null) {
                                        sendMessage(sender, "<red>Cannot find player.  Are they online?");
                                        return 1;
                                    }

                                    // Skipping if the player is already frozen
                                    if (frozen.contains(player.getName())) {
                                        sendMessage(sender, "<red>" + player.getName() + " is already frozen");
                                        return 1;
                                    }

                                    // Freezing the player
                                    frozen.add(player.getName());
                                    player.lockFreezeTicks(true);
                                    player.setFreezeTicks(139);
                                    sendMessage(player, "<aqua>You are frozen!  You cannot move until a moderator unfreezes you.");
                                    sendMessage(sender, "<green>Froze " + player.getName());

                                    writeConfig();
                                    return 1;
                                })
                        )
                )
        );

        // Unfreezes a player on a team or their whole team
        root.then(Commands.literal("unfreeze")
                .then(Commands.literal("team")
                        .then(Commands.argument("teamName", StringArgumentType.string())
                                .suggests((ctx, builder) -> {
                                    for (String team : teams) builder.suggest(team);

                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    CommandSender sender = ctx.getSource().getSender();
                                    String teamName = ctx.getArgument("teamName", String.class);

                                    // Cancels if the team doesn't exist
                                    if (!teams.contains(teamName)) {
                                        sendMessage(sender, "<red>Team " + teamName + " does not exist");
                                        return 1;
                                    }

                                    // Adding all the members on the team to the frozen list
                                    for (String member : teamMembers.get(teamName)) {
                                        Player player = getServer().getPlayer(member);

                                        // Skipping if the player is offline
                                        if (player == null) {
                                            sendMessage(sender, "<red>Cannot find " + member + ".  Are they online?");
                                            continue;
                                        }

                                        // Skipping if the player is not frozen
                                        if (!frozen.contains(member)) {
                                            sendMessage(sender, "<red>" + member + " is not frozen");
                                            continue;
                                        }

                                        // Unfreezing the player
                                        frozen.remove(member);
                                        player.lockFreezeTicks(false);
                                        player.setFreezeTicks(0);
                                        sendMessage(sender, "<green>Unfroze " + member);
                                    }

                                    writeConfig();
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("player")
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(ctx -> {
                                    CommandSender sender = ctx.getSource().getSender();
                                    Player player = ctx.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource()).getFirst();

                                    // Skipping if the player is offline
                                    if (player == null) {
                                        sendMessage(sender, "<red>Cannot find player.  Are they online?");
                                        return 1;
                                    }

                                    // Skipping if the player is not frozen
                                    if (!frozen.contains(player.getName())) {
                                        sendMessage(sender, "<red>" + player.getName() + " is not frozen");
                                        return 1;
                                    }

                                    // Unfreezing the player
                                    frozen.remove(player.getName());
                                    player.lockFreezeTicks(false);
                                    player.setFreezeTicks(0);
                                    sendMessage(sender, "<green>Unfroze " + player.getName());

                                    writeConfig();
                                    return 1;
                                })
                        )
                )
        );

        // Reloads the config
        root.then(Commands.literal("reload")
                .executes(ctx -> {
                    loadConfig();
                    return 1;
                })
        );

        // Registering commands
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            commands.registrar().register(root.build(), Arrays.asList("pt", "pteams"));
        });
    }

    // Updates the lists and maps with values from the config
    public void loadConfig() {
        teams = new ArrayList<>();
        teamMembers = new HashMap<>();
        teamWarps = new HashMap<>();
        frozen = new ArrayList<>();

        frozen.addAll(config.getStringList("frozen"));

        ConfigurationSection teamConfig = config.getConfigurationSection("teams");

        // Stopping if the teams object is not created
        if (teamConfig == null) return;

        teamConfig.getKeys(false).forEach(teamName -> {
            teams.add(teamName);
            teamMembers.put(teamName, config.getStringList("teams." + teamName + ".members"));

            if (config.contains("teams."+teamName+".warp_point")) {
                World world = getServer().getWorld(config.getString("teams." + teamName + ".warp_point.world"));
                double x = config.getDouble("teams." + teamName + ".warp_point.x");
                double y = config.getDouble("teams." + teamName + ".warp_point.y");
                double z = config.getDouble("teams." + teamName + ".warp_point.z");
                teamWarps.put(teamName, new Location(world, x, y, z));
            }

            teamMembers.values().forEach(memberList -> {
                memberList.forEach(member -> {
                    playerToTeamName.put(member, teamName);
                });
            });
        });
    }

    // Updates the config with values from the lists and maps
    public void writeConfig() {
        for (String team : teams) {
            if (config.getConfigurationSection("teams") == null) config.createSection("teams");
            if (config.getConfigurationSection("teams." + team) == null) config.createSection("teams." + team);
            config.set("teams." + team + ".members", teamMembers.get(team));

            if (teamWarps.containsKey(team)) {
                Location loc = teamWarps.get(team);

                config.set("teams." + team + ".warp_point.world", loc.getWorld().getName());
                config.set("teams." + team + ".warp_point.x", loc.x());
                config.set("teams." + team + ".warp_point.y", loc.y());
                config.set("teams." + team + ".warp_point.z", loc.z());
            }

            config.set("frozen", frozen);
        }

        saveConfig();
        loadConfig();
    }

    // Utility function that helps me remember to deserialize the messages before sending them
    public void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(serializer.deserialize(message));
    }

    // Stops players from moving if they are frozen
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Cancelling the event for frozen players
        if (frozen.contains(player.getName())) {
            // Allowing frozen players to look around
            if (event.hasChangedOrientation() && !event.hasChangedPosition()) {
                return;
            }

            sendMessage(player, "<aqua>You are frozen!  You cannot move until an event host unfreezes you.");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        if (frozen.contains(player.getName())) {
            sendMessage(player, "<aqua>You are frozen!  You cannot move until an event host unfreezes you.");
            event.setCancelled(true);
        }
    }
}