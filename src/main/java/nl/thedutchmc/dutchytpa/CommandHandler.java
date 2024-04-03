package nl.thedutchmc.dutchytpa;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class CommandHandler implements CommandExecutor {
    private final Tpa plugin;
    private final HashMap<String, Location> waypoints = new HashMap<String, Location>();
    private RandomAccessFile wpFile;
    private final Object fileLock = new Object();

    public CommandHandler(Tpa plugin) {
        this.plugin = plugin;
		File dataDir = plugin.getDataFolder();
		dataDir.mkdir();
    	File file = new File(dataDir, "waypoint.bin");
        try {
    		file.createNewFile();
    		// Don't use rwd, it's slow as hell
    		wpFile = new RandomAccessFile(file, "rw");
			readWayPoints();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
    }

    static HashMap<UUID, UUID> targetMap = new HashMap<>();
    
    private void readWayPoints() throws IOException {
		synchronized(fileLock) {
 	    	waypoints.clear();
	    	wpFile.seek(0);
			while(wpFile.getFilePointer() < wpFile.length()) {
				String wayptName = wpFile.readUTF();
				int x = wpFile.readInt();
				int z = wpFile.readInt();
				int y = wpFile.readShort();
				long uuidH = wpFile.readLong();
				long uuidL = wpFile.readLong();
				UUID uuid = new UUID(uuidH, uuidL);
				World wpWorld = plugin.getServer().getWorld(uuid);
				if(wpWorld == null) {
					System.out.println("Can't found the world of this waypoint entry");
				}
				Location location = new Location(wpWorld, x, y, z);
				waypoints.put(wayptName, location);
			}   			
		}
    }
    
    private void writeWayPoints() {
		try {
			wpFile.setLength(0);
			for(String key:waypoints.keySet()) {
				Location location = waypoints.get(key);
				wpFile.writeUTF(key);
	    		wpFile.writeInt(location.getBlockX());
	    		wpFile.writeInt(location.getBlockZ());
	    		wpFile.writeShort(location.getBlockY());
				UUID worldUUID = location.getWorld().getUID();
				wpFile.writeLong(worldUUID.getMostSignificantBits());
				wpFile.writeLong(worldUUID.getLeastSignificantBits());
			}
			wpFile.getChannel().force(false);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }

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
    			waypoints.put(args[2], playerLocation);
    			writeWayPoints();
    			sender.sendMessage(ChatColor.BLUE + "Location "+args[2]+" is added to the waypoint list!");
    		}
    	} else if(args[1].equals("list")) {
    		if(waypoints.size()==0) {
    			sender.sendMessage(ChatColor.RED + "No location is defined yet!");
    		}else {
    			for(String key:waypoints.keySet()) {
    				Location loc = waypoints.get(key);
    				if(!loc.getWorld().getName().equals(playerWorld)) {
    					sender.sendMessage(ChatColor.BLUE + "Location: " + key + ", world: "+loc.getWorld().getName());
    				}else {
    					int cost = calculateTeleportCost(playerLocation, loc);
        				sender.sendMessage(ChatColor.BLUE + "Location: " + key + ", cost: "+cost);        					
    				}
    					
    			}
    		}
    	} else if(args[1].equals("go")) {
    		if(args.length<3) {
    			sender.sendMessage(ChatColor.RED + "No location name is given!");
    		}else {
    			Location pt = waypoints.get(args[2]);
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
		    				pt = pt.clone();
        					while(!pt.getBlock().isEmpty()) {
        						pt.add(0, 1, 0);
        					}
        					senderP.teleport(pt);
        				}
        				
    				}else {
    					sender.sendMessage(ChatColor.RED + "You can't teleport to a waypoint of a different world!");
    				}
    			}
    		}
    	} else {
    		sender.sendMessage(ChatColor.RED + "Invalid action, we accept tpa location go, tpa location add and tpa location list");
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
        	synchronized(fileLock) {
        		this.handleTpaLocationCommand(sender, args);
        	}
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