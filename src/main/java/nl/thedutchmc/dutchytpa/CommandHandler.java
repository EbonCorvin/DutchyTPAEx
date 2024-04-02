package nl.thedutchmc.dutchytpa;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class CommandHandler implements CommandExecutor {
    private final Tpa plugin;
    
    // TODO: Serialize and deserialize the waypoint list
    private final HashMap<String, Location> teleportPoint;

    public CommandHandler(Tpa plugin) {
        this.plugin = plugin;
		this.teleportPoint = new HashMap<String, Location>();
    }

    static HashMap<UUID, UUID> targetMap = new HashMap<>();

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players may use this command!");
            return true;
        }

        if (command.getName().equals("tpa")) {
            handleTpaCommand(sender, args);
            return true;
        }

        if (command.getName().equals("tpaccept") || command.getName().equals("tpyes")) {
            handleTpaAcceptCommad(sender);
            return true;
        }

        if (command.getName().equals("tpdeny") || command.getName().equals("tpno")) {
            handleTpaDenyCommand(sender);
            return true;
        }

        return false;
    }

    private void handleTpaDenyCommand(CommandSender sender) {
        if (!sender.hasPermission("tpa.deny")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command!");
            return;
        }

        final Player senderP = (Player) sender;

        // Check if a request exists
        if (!targetMap.containsValue(senderP.getUniqueId())) {
            sender.sendMessage(ChatColor.GOLD + "You don't have any pending requests!");
            return;
        }

        for (Map.Entry<UUID, UUID> entry : targetMap.entrySet()) {
            if (entry.getValue().equals(senderP.getUniqueId())) {
                targetMap.remove(entry.getKey());
                Player originalSender = Bukkit.getPlayer(entry.getKey());
                originalSender.sendMessage(ChatColor.GOLD + "Your TPA request was denied!");
                sender.sendMessage(ChatColor.GOLD + "Denied TPA request.");
                break;
            }
        }
    }

    private void handleTpaAcceptCommad(CommandSender sender) {
        // Check permission
        if (!sender.hasPermission("tpa.accept")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command!");
            return;
        }

        final Player senderP = (Player) sender;

        // Check if there's a pending TPA request
        if (!targetMap.containsValue(senderP.getUniqueId())) {
            sender.sendMessage(ChatColor.GOLD + "You don't have any pending requests!");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "TPA request accepted!");

        for (Map.Entry<UUID, UUID> entry : targetMap.entrySet()) {
            if (entry.getValue().equals(senderP.getUniqueId())) {
                Player tpRequester = Bukkit.getPlayer(entry.getKey());

                // Fire the successful TPA event.
                // Used for integration with other plugins
                SuccessfulTpaEvent event = new SuccessfulTpaEvent(tpRequester, tpRequester.getLocation());
                Bukkit.getPluginManager().callEvent(event);
                
                // Teleport the player
                tpRequester.teleport(senderP);

                // Remove the pending request
                targetMap.remove(entry.getKey());
                break;
            }
        }
    }

    private void handleTpaLocationCommand(CommandSender sender, String[] args) {
    	final Player senderP = (Player) sender;
        Location playerLocation = senderP.getLocation();
        String playerWorld = playerLocation.getWorld().getName();	
    	// TODO: Check if the user can add new waypoint (i.e. Op)
    	if(args[1].equals("add")) {
    		if(args.length<3) {
    			sender.sendMessage(ChatColor.RED + "No location name is given!");
    		}else {
    			teleportPoint.put(args[2], playerLocation);
    			sender.sendMessage(ChatColor.BLUE + "Location "+args[2]+" is added to the waypoint list!");
    		}
    	}
    	if(args[1].equals("list")) {
    		if(teleportPoint.size()==0) {
    			sender.sendMessage(ChatColor.RED + "No location is defined yet!");
    		}else {
    			for(String key:teleportPoint.keySet()) {
    				Location loc = teleportPoint.get(key);
    				if(!loc.getWorld().getName().equals(playerWorld)) {
    					sender.sendMessage(ChatColor.BLUE + "Location: " + key + ", world: "+loc.getWorld().getName());
    				}else {
    					int cost = calculateTeleportCost(playerLocation, loc);
        				sender.sendMessage(ChatColor.BLUE + "Location: " + key + ", cost: "+cost);        					
    				}
    					
    			}
    		}
    	}

    	if(args[1].equals("go")) {
    		if(args.length<3) {
    			sender.sendMessage(ChatColor.RED + "No location name is given!");
    		}else {
    			Location pt = teleportPoint.get(args[2]);
    			if(pt==null) {
    				sender.sendMessage(ChatColor.RED + "The given location name does not exist!");
    			}else {
    				if(pt.getWorld().getName().equals(playerWorld)) {
        				int cost = calculateTeleportCost(playerLocation, pt);
        				int remainingLevel = (int) (senderP.getLevel() - cost);
        				if(remainingLevel < 0) {
        					sender.sendMessage(ChatColor.RED + "You don't have enough level to pay for the teleport fee!");
        				}else {
	        				sender.sendMessage(ChatColor.BLUE + "Teleporting to " + args[2] + ", it will cost you " + cost + " experience level.");
	        				senderP.setLevel(remainingLevel);
	        				senderP.teleport(pt);
        				}
        				
    				}else {
    					sender.sendMessage(ChatColor.RED + "You can't teleport to a waypoint of a different world!");
    				}
    			}
    		}
    	}
    }
    
    private void handleTpaCommand(CommandSender sender, String[] args) {
        // Check permissions
        if (!sender.hasPermission("tpa.tpa")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command!");
            return;
        }

        // Check if a player name was provided
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Invalid syntax!");
            return;
        }
                
        if(args[0].equals("location")) {
        	this.handleTpaLocationCommand(sender, args);
        	return;
        }

        // Check if the target player exists
        if (!Bukkit.getOnlinePlayers().contains(Bukkit.getPlayer(args[0]))) {
            sender.sendMessage(ChatColor.RED + "Player is not online!");
            return;
        }

        Player target = Bukkit.getPlayer(args[0]);
        final Player senderP = (Player) sender;

        // Check if the target player is the requesting player
        if (target.getUniqueId().equals(senderP.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "You may not teleport to yourself!");
            return;
        }

        // Check if there's a pending request
        if (targetMap.containsKey(senderP.getUniqueId())) {
            sender.sendMessage(ChatColor.GOLD + "You already have a pending request!");
            return;
        }

        // Send the target a message informing them of the request
        target.sendMessage(ChatColor.RED + senderP.getName() + ChatColor.GOLD + " wants to teleport to you. \nType " + ChatColor.RED + "/tpaccept" + ChatColor.GOLD + " to accept this request.\nType " + ChatColor.RED + "/tpdeny" + ChatColor.GOLD + " to deny this request.\nYou have 5 minutes to respond.");
        targetMap.put(senderP.getUniqueId(), target.getUniqueId());

        // Inform the requester that the request was sent
        sender.sendMessage(ChatColor.GOLD + "Send TPA request to " + ChatColor.RED + target.getName());

        // Remove the request after a certain time
        (new BukkitRunnable() {
            public void run() {
                CommandHandler.targetMap.remove(senderP.getUniqueId());
            }
        }).runTaskLaterAsynchronously(this.plugin, 6000L);
    }

    // TODO: Consider allowing player to pay with other item other than xp level
    private int calculateTeleportCost(Location pt1, Location pt2) {
		long distance = Math.round(pt1.distance(pt2));
		int cost = Math.round(distance / 16 / 10);
		return cost;
    }
}