package com.dechiridas.bitauth.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.dechiridas.bitauth.BitAuth;

public class Logout implements CommandExecutor {
	private BitAuth plugin;
	
	public Logout(BitAuth instance) {
		this.plugin = instance;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] split) {
		boolean r = false;
		
		if (sender instanceof Player) {
			Player player = (Player)sender;
			
			if (plugin.pex.has(player, "bitauth.logout") || player.isOp())
				plugin.database.tryLogout(player, split);
			else
				player.sendMessage(ChatColor.YELLOW +
						"You do not have access to this feature.");
			
			r = true;
		} else {
			plugin.database.tryLogoutFromConsole(split);
			
			r = true;
		}
		
		return r;
	}
}
