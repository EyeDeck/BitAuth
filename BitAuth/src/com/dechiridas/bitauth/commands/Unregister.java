package com.dechiridas.bitauth.commands;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.dechiridas.bitauth.BitAuth;

public class Unregister implements CommandExecutor {
	private BitAuth plugin;
	public static Map<Player, Long> confirmUnregister = new HashMap<Player, Long>();
	
	public Unregister(BitAuth instance) {
		this.plugin = instance;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] split) {
		boolean r = false;
		
		if (split.length == 0) {
			if (sender instanceof Player) {
				Player player = (Player)sender;

				player.sendMessage(ChatColor.YELLOW + "Confirm your unregistration by typing /unregister <password>");
				confirmUnregister.put(player, System.nanoTime());
			} else
				plugin.log.println("This command requires an argument when sent from the console.");

			r = true;
		} else if (split.length == 1) {
			if (confirmUnregister.containsKey(sender)) {
				Player player = (Player)sender;
				
				 // Give unregister a 1-minute timeout
				if (System.nanoTime() >= confirmUnregister.get(player) + 60000000000L) {
					if (plugin.database.tryVerifyPassword(player, split[0]))
						plugin.database.tryUnregister(player);
					else
						player.sendMessage(ChatColor.YELLOW
								+ "Password incorrect, type /unregister to try again.");
				} else {
					player.sendMessage(ChatColor.YELLOW
							+ "Unregister has timed out! Type /unregister to try again.");
				}
				
				confirmUnregister.remove(player);
				
				return true;
			}
			if (sender.hasPermission("bitauth.unregister.other")) {
				Player player = Bukkit.getServer().getPlayer(split[0]);
				
				if (player == sender)
					player.sendMessage(ChatColor.YELLOW + "You cannot unregister yourself this way!");
				else if (player == null) {
					if (sender instanceof Player)
						sender.sendMessage(ChatColor.YELLOW + "Player \"" + split[0] + "\" could not be found.");
					else
						plugin.log.println("Player \"" + split[0] + "\" could not be found.");
				}
				else
					plugin.database.tryUnregister(player);
				
				r = true;
			} else
				sender.sendMessage(ChatColor.RED + 
						"You do not have access /unregister <username>.");
		}
		
		return r;
	}
}
