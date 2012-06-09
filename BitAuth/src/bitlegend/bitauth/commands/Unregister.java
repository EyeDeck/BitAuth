package bitlegend.bitauth.commands;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import bitlegend.bitauth.BitAuth;

public class Unregister implements CommandExecutor {
	private BitAuth instance;
	
	// Database info
	private String user = "";
	private String pass = "";
	private String url = "";
	private String logintable = "";
	
	public Unregister(BitAuth instance) {
		this.instance = instance;
		user = instance.config.readString("DB_User");
		pass = instance.config.readString("DB_Pass");
		url = "jdbc:mysql://" + instance.config.readString("DB_Host") + 
				"/" + instance.config.readString("DB_Name");
		logintable = instance.config.readString("DB_Table_login");
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] split) {
		boolean r = false;
		
		if (sender instanceof Player) {
			Player player = (Player)sender;
			
			try {
				// Create connection
				Connection conn = DriverManager.getConnection(url, user, pass);
				String query = "DELETE FROM `" + logintable + "` WHERE username='" + player.getName() + "'";
				Statement delete = conn.createStatement();
				delete.executeUpdate(query);
				
				// Remove player from loggedIn list
				int index = 0;
				for (Player p : instance.loggedIn) {
					if (p.getName().equals(player.getName()))
						index = instance.loggedIn.indexOf(p);
				}
				instance.loggedIn.remove(index);
				
				// Add player to unregistered list
				instance.unregistered.add(player);
				
				// Inform the player that they have been unregistered from the server
				player.sendMessage(ChatColor.GREEN + "Successfully unregistered, goodbye!");
				r = true;
				
			} catch (SQLException se) {
				se.printStackTrace();
			}
		}
		else {
			instance.logInfo("This is an in-game only command.");
			r = true;
		}
		return r;
	}

}
