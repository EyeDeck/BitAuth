package com.dechiridas.bitauth.util;

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ConcurrentModificationException;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerLoginEvent;

import com.dechiridas.bitauth.BitAuth;
import com.dechiridas.bitauth.player.BAPlayer;
import com.dechiridas.bitauth.player.BAState;

public class Database {
	private BitAuth plugin;
	
	// Database info
	private String login = ""; // db login table
	private String iptable = ""; // db ip table
	private String wlist = ""; // db whitelist table
	
	// Whitelist info
	private int bypass = 0;
		
	public Database(BitAuth instance) {
		this.plugin = instance;
		
		// Database configuration
		login = plugin.config.getString("settings.database.tables.login");
		wlist = plugin.config.getString("settings.database.tables.whitelist");
		iptable = plugin.config.getString("settings.database.tables.iphistory");
		
		// Whitelist configuration
		bypass = plugin.config.getInt("settings.whitelist.bypass");
	}
	
	public boolean whitelistEnabled() {
		return plugin.config.getBoolean("settings.whitelist.enabled");
	}
	
	public int getWhitelistBypassTime() {
		return bypass;
	}
	
	public boolean isWhitelisted(Player player) {
		boolean r = false;
		
		try {
			Connection conn = plugin.connectionmanager.getConnection();
			Statement select = conn.createStatement();
			ResultSet result = select.executeQuery(
					"SELECT * FROM `" + wlist + "`");
			while (result.next()) {
				String user = result.getString(1);
				boolean allowed = (result.getInt(2) == 1 ? true : false);
				if (user.equalsIgnoreCase(player.getName())) // Player found
					if (allowed == true)
						r = true;
			}
			result.close();
			select.close();
		} catch (SQLException sq) {
			sq.printStackTrace();
		}
		
		return r;
	}
	
	public void tryLogin(Player player, PlayerLoginEvent event) {
		try {
			Connection conn = plugin.connectionmanager.getConnection();
			Statement select = conn.createStatement();
			ResultSet result = select.executeQuery(
					"SELECT * FROM `" + login + "`");
			
			BAPlayer ba = new BAPlayer(plugin, player);
			
			if (result.next()) { // Results found
				boolean playerFound = false;
				do {
					String username = result.getString(1);
					boolean pwreset = result.getInt(8) == 1 ? true : false;
					if (username.equalsIgnoreCase(player.getName())) { // Player found
						playerFound = true;
						long lastlogintest = (System.currentTimeMillis() / 1000L) - 1800;
						long lastlogintime = result.getLong(5);
						long playerIP = result.getLong(7);
						long currentIP = Utils.ipToLong(event.getAddress().getHostAddress());

						if (lastlogintime < lastlogintest) {
							ba.setState(BAState.LOGGEDOUT);
							
						} else {
							// Fix to prevent name spoofing from taking advantage
							// of the 30 minute grace period
							if (currentIP != playerIP) {
								plugin.log.println("WARNING: Possible name-spoofer detected using name: "
										+ player.getName());

								ba.setState(BAState.LOGGEDOUT);
							}
							else {
								//playerLoggedIn = true;
								ba.setState(BAState.LOGGEDIN);
							}
						}
						
						if (pwreset == true) // Password reset
							ba.setState(BAState.PWRESET);

						if (ba.getState() == BAState.LOGGEDIN) {								
							player.sendMessage(ChatColor.GREEN +
									"Welcome back, " + player.getName());
						}
					}
				} while (result.next());
				
				if (playerFound == false) { // Player data not found
					ba.setState(BAState.UNREGISTERED);
				}
			}
			else { // No records found, create new for this player
				ba.setState(BAState.UNREGISTERED);
			}
			
			plugin.pman.addPlayer(ba);
			
			result.close();
			select.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public boolean tablesExist() {
		boolean r = false, blogin = false, bwhitelist = false, bip = false;
		
		try {
			Connection conn = plugin.connectionmanager.getConnection();
			Statement select = conn.createStatement();
			ResultSet result = select.executeQuery("SHOW TABLES");
			
			if (result.next()) { // Results found
				do {
					String table = result.getString(1);
					if (table.equals(login))
						blogin = true;
					if (table.equals(wlist))
						bwhitelist = true;
					if (table.equals(iptable))
						bip = true;
				} while (result.next());
			}
		} catch (SQLException se) {
			se.printStackTrace();
		}
		
		if (blogin == true && bwhitelist == true && bip == true)
			r = true;
		
		if (blogin == false)
			generateTableLogin();
		if (bwhitelist == false)
			generateTableWhitelist();
		if (bip == false)
			generateTableIPHistory();

		// check for changes if tables exist, otherwise ignore
		if (r == true)
			checkChanges();
		
		return r;
	}
	
	private void checkChanges() {
		// check for new login table columns
		try {
			// new columns
			String[] newcolumns = { "dateregistered" };
			boolean[] found = new boolean[newcolumns.length];
			
			Connection conn = plugin.connectionmanager.getConnection();
			Statement select = conn.createStatement();
			ResultSet result = select.executeQuery("SELECT * FROM `" + login + "`");
			ResultSetMetaData rsmd = result.getMetaData();
			
			for (int i = 1; i < rsmd.getColumnCount() + 1; i++) {
				String column = rsmd.getColumnName(i);
				
				for (int j = 0; j < newcolumns.length; j++)
					if (newcolumns[j].equals(column))
						found[j] = true; // column found
			}
			
			for (int i = 0; i < found.length; i++) {
				if (found[i] == false) { // column not found
					String column = newcolumns[i];
					
					// date registered column
					if (column.equals(newcolumns[i])) {
						plugin.log.println("Adding column " + column + " to table " + login + ".");
						
						PreparedStatement ps = conn.prepareStatement(
							"ALTER TABLE `" + login + "` ADD " +
							"`" + column + "` BIGINT(20) NOT NULL " +
							"AFTER `lastlogintime`");
						ps.executeUpdate();
						ps.close();
					}
				}
			}
			
			// clean up
			result.close();
			select.close();
		} catch (SQLException se) {
			se.printStackTrace();
		}
	}
	
	private void generateTableLogin() {
		String queryDropLogin = "DROP TABLE IF EXISTS `" + login + "`";
		String queryCreateLogin = "CREATE TABLE `" + login + "` (" +
				"`username` text NOT NULL," +
				"`salt` blob NOT NULL," +
				"`password` blob NOT NULL," +
				"`whitelist` tinyint(1) NOT NULL," +
				"`lastlogintime` bigint(20) NOT NULL," +
				"`dateregistered` bigint(20) NOT NULL," +
				"`ipaddress` bigint(20) NOT NULL," +
				"`enableipcheck` tinyint(1) NOT NULL," +
				"`pwreset` tinyint(4) NOT NULL," +
				"`temppwd` blob," +
				"`tmpsalt` blob" +
				") ENGINE=InnoDB DEFAULT CHARSET=utf8";
		
		try {
			Connection conn = plugin.connectionmanager.getConnection();
			Statement query = conn.createStatement();
			
			query.executeUpdate(queryDropLogin);
			query.executeUpdate(queryCreateLogin);
			
			query.close();
		} catch (SQLException se) {
			se.printStackTrace();
		}
	}
	
	private void generateTableWhitelist() {
		String queryDropWhitelist = "DROP TABLE IF EXISTS `" + wlist +"`";
		String queryCreateWhitelist = "CREATE TABLE `" + wlist + "` (" +
				"`username` text NOT NULL," +
				"`enabled` tinyint(4) NOT NULL" +
				") ENGINE=InnoDB DEFAULT CHARSET=utf8";
		
		try {
			Connection conn = plugin.connectionmanager.getConnection();
			Statement query = conn.createStatement();
			
			query.executeUpdate(queryDropWhitelist);
			query.executeUpdate(queryCreateWhitelist);
			
			query.close();
		} catch (SQLException se) {
			se.printStackTrace();
		}
	}
	
	private void generateTableIPHistory() {
		String queryDropIp = "DROP TABLE IF EXISTS `" + iptable + "`";
		String queryCreateIp = "CREATE TABLE `" + iptable + "` (" +
				"`username` text NOT NULL," +
				"`ip1` text," +
				"`ip2` text," +
				"`ip3` text," +
				"`ip4` text," +
				"`ip5` text" +
				") ENGINE=InnoDB DEFAULT CHARSET=utf8";
		
		try {
			Connection conn = plugin.connectionmanager.getConnection();
			Statement query = conn.createStatement();
			
			query.executeUpdate(queryDropIp);
			query.executeUpdate(queryCreateIp);
			
			query.close();
		} catch (SQLException se) {
			se.printStackTrace();
		}
	}
	
	public String getPlayerIPHistoryByIndex(String name, int index) {
		String ip = "";
		index = (index > 5 ? 5 : index) < 0 ? 0 : index;
		
		try {
			Connection conn = plugin.connectionmanager.getConnection();
			Statement select = conn.createStatement();
			ResultSet result = select.executeQuery("SELECT * FROM `" + iptable + "` " +
				"WHERE `username`='" + name + "'");
			
			if (result.next())
				ip = result.getString(1 + index);
		} catch (SQLException se) {
			se.printStackTrace();
		}
		
		return ip;
	}
	
	public String[] getPlayerIPHistory(String name) {
		String[] history = new String[5];
		
		for (int i = 1; i <= 5; i++) {
			history[i - 1] = getPlayerIPHistoryByIndex(name, i);
		}
		
		return history;
	}
	
	public String[] getPlayerIPHistory(Player player) {
		return getPlayerIPHistory(player.getName());
	}
	
	public String getPlayerIPHistoryMostRecent(String name) {
		return getPlayerIPHistoryByIndex(name, 1);
	}
	
	public String getPlayerIPHistoryMostRecent(Player player) {
		return getPlayerIPHistoryByIndex(player.getName(), 1);
	}
	
	private void addPlayerIPRow(Player player, String ip) {
		try {
			Connection conn = plugin.connectionmanager.getConnection();
			Statement update = conn.createStatement();
			String query = "INSERT INTO `" + iptable + "` " +
				"(`username`, `ip1`) VALUES('" + player.getName() + "', '" + ip + "')";
			
			update.executeUpdate(query);
			
			update.close();
		} catch (SQLException se) {
			se.printStackTrace();
		}
	}
	
	public void offsetPlayerIPHistory(Player player) {
		try {
			Connection conn = plugin.connectionmanager.getConnection();
			Statement update = conn.createStatement();
			String[] iphist = getPlayerIPHistory(player);
			String[] offset = new String[iphist.length];
			String currentip = player.getAddress().getAddress().getHostAddress();
			String recent = getPlayerIPHistoryMostRecent(player);

			if (recent == null || recent.equals("")) {
				addPlayerIPRow(player, currentip);
				recent = currentip;
			}

			if (!currentip.equals(recent)) {
				for (int i = iphist.length - 1; i >= 0; i--) {
					if (i > 1)
						offset[i] = iphist[i - 1];
					if (i == 1)
						offset[i] = recent;
					if (i == 0)
						offset[i] = currentip;
				}
				
				String query = "UPDATE `" + iptable + "` SET " +
					"`ip1`='"+ offset[0] + "', " +
					"`ip2`='"+ offset[1] + "', " +
					"`ip3`='"+ offset[2] + "', " +
					"`ip4`='"+ offset[3] + "', " +
					"`ip5`='"+ offset[4] + "' " +
					"WHERE `username`='" + player.getName() + "'";

				update.executeUpdate(query);
			}
			
			update.close();
		} catch (SQLException se) {
			se.printStackTrace();
		}
	}
	
	public boolean playerExists(String name) {
		boolean found = false;
		
		try {
			Connection conn = plugin.connectionmanager.getConnection();
			Statement select = conn.createStatement();
			ResultSet result = select.executeQuery(
				"SELECT * FROM `" + iptable + "` WHERE `username`='" + name + "'");
			if (result.next())
				found = true;
		} catch (SQLException se) {
			se.printStackTrace();
		}
		
		return found;
	}

	public void tryRegister(Player player, String password) {
		BAPlayer ba = plugin.pman.getBAPlayerByName(player.getName());
		try {
			// Get connection
			Connection conn = plugin.connectionmanager.getConnection();
			
			// Check for pre-existing user by this name
			Statement select = conn.createStatement();
			ResultSet result = select.executeQuery(
					"SELECT * FROM `" + login + "`");
			
			boolean playerFound = false;
			
			if (result.next()) { // Results found
				do {
					String username = result.getString(1);
					if (username.equals(player.getName())) { // Player data found
						player.sendMessage(ChatColor.GREEN
								+ "Error: user by the name of "
								+ player.getName()
								+ " already found");
						playerFound = true;
					}
				} while (result.next());
			}
			
			if (playerFound == false) { // Player not found
				// Generate new salt and password from salt and input
				byte[] salt = HashManager.GenerateSalt();
				byte[] hash = HashManager.GenerateHash(password, salt);

				// Get unix time stamp
				long unixtime = Utils.unixTimestamp();

				// Get player IP as long
				InetAddress iAddress = player.getAddress().getAddress();
				long playerIP = Utils.ipToLong(iAddress.getHostAddress());

				// Set up the query
				String query = "INSERT INTO `" + login + "` "
						+ "(`username`, `salt`, `password`, `whitelist`, "
						+ "`lastlogintime`, `dateregistered`, `ipaddress`, `enableipcheck`, "
						+ "`pwreset`, `temppwd`, `tmpsalt`) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
				PreparedStatement statement = conn.prepareStatement(query);

				// player name
				statement.setString(1, player.getName());
				
				// password salt
				statement.setBytes(2, salt);
				
				// hashed password
				statement.setBytes(3, hash);
				
				// whitelisted - not used
				statement.setBoolean(4, true);
				
				// last login time - now
				statement.setLong(5, unixtime);
				
				// date registered - now
				statement.setLong(6, unixtime);
				
				// ip address
				statement.setLong(7, playerIP);
				
				// enable ip checking
				statement.setBoolean(8, false);
				
				// password reset (y/n)
				statement.setBoolean(9, false);
				
				// temp password hash
				statement.setBytes(10, null);
				
				// temp salt
				statement.setBytes(11, null);

				// Execute the query
				statement.executeUpdate();

				// Close statement
				statement.close();

				// Set the player state to logged in
				ba.setState(BAState.LOGGEDIN);

				player.sendMessage(ChatColor.GREEN
						+ "Thank you for registering with our server!");
			}
			// Close result and connection
			result.close();			
		} catch (ConcurrentModificationException cmx) {
			cmx.printStackTrace();
		} catch (UnsupportedEncodingException uex) {
			uex.printStackTrace();
		} catch (NoSuchAlgorithmException nsax) {
			nsax.printStackTrace();
		} catch (SQLException se) {
			se.printStackTrace();
		}
	}
	
	public boolean tryLoginManual(Player player, String[] input) {
		BAPlayer ba = plugin.pman.getBAPlayerByName(player.getName());
		boolean r = false;
		try {
			if (ba.getState() == BAState.LOGGEDOUT) {
				// Get connection
				Connection conn = plugin.connectionmanager.getConnection();
				
				Statement select = conn.createStatement();
				ResultSet result = select.executeQuery(
						"SELECT * FROM `" + login + "` WHERE username='" + player.getName() + "'");
				if (result.next()) { // Results found
					byte[] salt = result.getBytes(2);
					byte[] hash = result.getBytes(3);
					byte[] passwordCheck = HashManager.GenerateHash(input[0], salt);
					boolean pwreset = result.getInt(9) == 1 ? true : false;
					long playerIP = result.getLong(7);
					boolean checkIP = result.getBoolean(8);
					
					// Get player IP
					InetAddress iAddress = player.getAddress().getAddress();
					long currentIP = Utils.ipToLong(iAddress.getHostAddress());
					
					// Check player IP if necessary
					if ((!checkIP) || (checkIP && playerIP == currentIP)) { // IP addresses match
						if (pwreset == true) {
							hash = result.getBytes(10);
							salt = result.getBytes(11);
							passwordCheck = HashManager.GenerateHash(input[0], salt);
							
							if (input.length == 1) { // They didn't include the new password or don't know their password has been reset
								player.sendMessage(ChatColor.YELLOW + "Reset your password: /login <temp passwd> <new passwd>");
								player.sendMessage(ChatColor.YELLOW + "The temp password is provided by a mod or admin");
							}
							else {
								if (Utils.byteToString(passwordCheck).equals(
										Utils.byteToString(hash))) { // Password matches temp password
									if (input.length == 2) { // Check to ensure newpass was entered too
										player.sendMessage(ChatColor.GREEN + 
												"Password reset properly, welcome back " + player.getName());
										
										// Get unix time stamp
										long unixtime = Utils.unixTimestamp();
										
										// Create new salt and password hash
										byte[] newsalt = HashManager.GenerateSalt();
										byte[] newhash = HashManager.GenerateHash(input[1], newsalt);
										
										// Create query
										String query = "UPDATE `"
												+ login
												+ "` SET `salt`= ?, `password`= ?, `pwreset`= ?, `temppwd`= ?, "
												+ "`tmpsalt`= ?, `lastlogintime`= ? WHERE `username`= ?";
										
										PreparedStatement update = conn.prepareStatement(query);
										
										// Set query values
										update.setBytes(1, newsalt);
										update.setBytes(2, newhash);
										update.setInt(3, 0);
										update.setBytes(4, null);
										update.setBytes(5, null);
										update.setLong(6, unixtime);
										update.setString(7, player.getName());
										
										// Execute query
										update.execute();
										
										// Add player to the logged in list
										ba.setState(BAState.LOGGEDIN);
										r = true;
									}
								}
								else { // Password doesn't match
									player.sendMessage(ChatColor.YELLOW + "Temp password does not match");
								}
							}
						}
						
						else if (Utils.byteToString(passwordCheck).equals(
								Utils.byteToString(hash))) { // Passwords match
							player.sendMessage(ChatColor.GREEN
									+ "Password correct, welcome back "
									+ player.getName());

							// Get unix time stamp
							long unixtime = Utils.unixTimestamp();

							// Set up the query
							String query = "UPDATE `" + login
									+ "` SET lastlogintime='" + unixtime
									+ "' WHERE username='"
									+ player.getName() + "'";
							PreparedStatement statement = conn
									.prepareStatement(query);
							statement.executeUpdate();

							// Add player to the logged in list
							ba.setState(BAState.LOGGEDIN);
							r = true;
						} else { // Passwords do not match
							player.sendMessage(ChatColor.YELLOW
									+ "Password incorrect, try again.");
						}
					}
					else { // IP addresses do not match
						player.sendMessage(new String[] {
								ChatColor.YELLOW
										+ "Looks like you are under lockdown and your IP doesn't match.",
								ChatColor.YELLOW
										+ "If this is an error, contact an admin in IRC." });
					}
				}
			}
			else if (ba.getState() == BAState.UNREGISTERED) {
				player.sendMessage(ChatColor.YELLOW + "You have not registered yet.");
			}
			else {
				player.sendMessage(ChatColor.YELLOW + "You are already logged in, silly!");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return r;
	}
	
	public ResultSet getPlayerRow(Player player) {
		ResultSet result = null;
		
		return result;
	}
	
	public void tryLogout(CommandSender sender, String name) {
		// Look for player
		BAPlayer ba = plugin.pman.getBAPlayerByName(name);
		
		// Remove the player from the loggedIn list
		ba.setState(BAState.LOGGEDOUT);
		
		try {
			// Get connection
			Connection conn = plugin.connectionmanager.getConnection();
			
			// Query to reset last login time to 1
			String query = "UPDATE `"
					+ login
					+ "` SET lastlogintime='1' WHERE username='"
					+ name + "'";
			
			// Create statement object
			Statement update = conn.createStatement();
			
			// Execute update
			update.executeUpdate(query);
			
			// Message sender
			if (sender instanceof Player)
				sender.sendMessage(ChatColor.YELLOW + "Player " + name + " has been logged out");
			else 
				sender.sendMessage("Player " + name + " has been logged out");
			
			// Message recipient
			ba.getPlayer().sendMessage(ChatColor.YELLOW + "You have been logged out");
			
			// Clean up
			update.close();
		} catch (SQLException se) {
			se.printStackTrace();
		}

	}
	
	public void tryUnregister(Player player) {
		BAPlayer ba = plugin.pman.getBAPlayerByName(player.getName());
		
		try {
			// Get connection
			Connection conn = plugin.connectionmanager.getConnection();
			
			String query = "DELETE FROM `" + login + "` WHERE username='" + player.getName() + "'";
			Statement delete = conn.createStatement();
			delete.executeUpdate(query);

			// Force player to log out
			plugin.getServer().dispatchCommand(Bukkit.getConsoleSender(), "logout " + player.getName());
			
			// Set player state to unregistered
			ba.setState(BAState.UNREGISTERED);
			
			// Inform the player that they have been unregistered from the server
			player.sendMessage(ChatColor.GREEN + "Successfully unregistered, goodbye!");
		} catch (SQLException se) {
			se.printStackTrace();
		}
	}
	
	public boolean tryVerifyPassword(Player player, String input) {
		try {
			Connection conn = plugin.connectionmanager.getConnection();
			Statement select = conn.createStatement();
			ResultSet result = select.executeQuery(
					"SELECT * FROM `" + login + "` WHERE username='" + player.getName() + "'");
			
			if (result.next()) { // Results found
				byte[] salt = result.getBytes(2);
				byte[] pwdhash = result.getBytes(3);
				
				byte[] pwdtest = HashManager.GenerateHash(input, salt);
				if (Utils.byteToString(pwdtest).equals(Utils.byteToString(pwdhash))) // input password correct
					return true;
				else // input password incorrect
					return false;
			}
			
			result.close();
		} catch (SQLException se) {
			se.printStackTrace();
		} catch (UnsupportedEncodingException uee) {
			uee.printStackTrace();
		} catch (NoSuchAlgorithmException nsae) {
			nsae.printStackTrace();
		}
		return false;
	}
	
	public boolean checkResetPassword(Player player) {
		boolean r = false;
		try {
			// Get connection
			Connection conn = plugin.connectionmanager.getConnection();
			
			Statement select = conn.createStatement();
			ResultSet result = select.executeQuery(
					"SELECT * FROM `" + login + "` WHERE username='" + player.getName() + "'");
			
			// If the player isn't found, it can be safely assumed that they're not under password reset
			if (result.next()) {
				r = result.getInt(9) == 1 ? true : false;
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return r;
	}
	
	public boolean modifyWhitelist(String[] input) {
		try {
			// Determine add or remove
			boolean addremove = false;
			if (input[0].equals("add"))
				addremove = true;
			if (input[0].equals("del"))
				addremove = false;
			
			// Get indicated user name
			String username = input[1];
			
			// Create connection and statement
			Connection conn = plugin.connectionmanager.getConnection();
			
			// Check for preexisting entry
			Statement select = conn.createStatement();
			ResultSet result = select.executeQuery(
					"SELECT * FROM `" + wlist + "`");
			
			boolean playerFound = false;
			if (result.next()) { // Results found
				do {
					String user = result.getString(1);
					if (user.equals(username)) { // Player data found
						playerFound = true;
					}
				} while (result.next());
			}
			
			if (playerFound == true) { // Update existing information
				String query = "UPDATE `" + wlist + "` SET enabled='"
						+ (addremove == true ? 1 : 0)
						+ "' WHERE username='" + username + "'";
				Statement update = conn.createStatement();
											
				update.executeUpdate(query);
				update.close();
			}
			if (playerFound == false) { // Insert new data
				String query = "INSERT INTO `" + wlist + "` " +
						"(`username`, `enabled`) VALUES (?,?)";
				PreparedStatement insert = conn.prepareCall(query);
				
				insert.setString(1, username);
				insert.setInt(2, (addremove == true ? 1 : 0));
				
				insert.executeUpdate();
				insert.close();
			}
			
			// Clean up
			result.close();
			
			// Report back to player
			return addremove;
		} catch (SQLException se) {
			se.printStackTrace();
		}
		return false;
	}
	
	public void toggleWhitelist(CommandSender sender, String[] input) {
		if (input[0].equalsIgnoreCase("enable")) { // Enable whitelist
			// Check current whitelist state
			boolean wl = plugin.config.getBoolean("settings.whitelist.enabled");
			if (wl == false) {
				plugin.config.set("settings.whitelist.enabled", true);
				if (sender instanceof Player)
					((Player)sender).sendMessage(ChatColor.GREEN + "Whitelist enabled");
				else
					plugin.log.println("Whitelist enabled");
			}
			else
				if (sender instanceof Player)
					((Player)sender).sendMessage(ChatColor.YELLOW + "Whitelist is already enabled");
				else
					plugin.log.println("Whitelist is already enabled");
		}
		if (input[0].equalsIgnoreCase("disable")) { // Disable whitelist
			// Check current whitelist state
			boolean wl = plugin.config.getBoolean("settings.whitelist.enabled");
			if (wl == true) {
				plugin.config.set("settings.whitelist.enabled", false);
				
				if (sender instanceof Player)
					((Player)sender).sendMessage(ChatColor.GREEN + "Whitelist disabled");
				else
					plugin.log.println("Whitelist disabled");
			}
			else
				if (sender instanceof Player)
					((Player)sender).sendMessage(ChatColor.YELLOW + "Whitelist is already disabled");
				else
					plugin.log.println("Whitelist is already disabled");
		}
	}

	public void tryIpcheck(Player player, String[] input) {
		boolean ipcheckFlag = false;
		int ipcheckFlagInt = 0;
		
		// Check the input
		if (input[0].equals("enable"))
			ipcheckFlag = true;
		if (input[0].equals("disable"))
			ipcheckFlag = false;
		
		if (ipcheckFlag == true)
			ipcheckFlagInt = 1;
		if (ipcheckFlag == false)
			ipcheckFlagInt = 0;
		
		try {
			// Create connection
			Connection conn = plugin.connectionmanager.getConnection();
			
			String query = "UPDATE `" + login + "` SET enableipcheck='"
					+ ipcheckFlagInt + "' WHERE username='"
					+ player.getName() + "'";
			Statement update = conn.createStatement();
			update.executeUpdate(query);
			
			update.close();
			
			player.sendMessage(ChatColor.GREEN
					+ "IP checking for your account has been "
					+ ((ipcheckFlag == true) ? "enabled" : "disabled"));
		} catch (SQLException se) {
			se.printStackTrace();
		}
	}

	public void tryChangePassword(Player player, String[] input) {
		String oldpasswd = input[0];
		String newpasswd = input[1];
		
		try {
			Connection conn = plugin.connectionmanager.getConnection();
			Statement select = conn.createStatement();
			ResultSet result = select.executeQuery(
					"SELECT * FROM `" + login + "` WHERE username='" + player.getName() + "'");
			
			if (result.next()) { // Results found
				byte[] salt = result.getBytes(2);
				byte[] pwdhash = result.getBytes(3);
				
				byte[] pwdtest = HashManager.GenerateHash(oldpasswd, salt);
				if (Utils.byteToString(pwdtest).equals(Utils.byteToString(pwdhash))) { // oldpasswd matches current password
					// Generate new salt for password and new password hash from the input + salt
					byte[] newsalt = HashManager.GenerateSalt();
					byte[] newpass = HashManager.GenerateHash(newpasswd, newsalt);

					// Create query and statement
					String query = "UPDATE `" + login
							+ "` SET `salt`= ?, `password`= ? " + "WHERE `username`= ?";
					PreparedStatement update = conn.prepareStatement(query);
					
					// Set the values in the statement
					update.setBytes(1, newsalt);
					update.setBytes(2, newpass);
					update.setString(3, player.getName());
					
					// Execute the statement
					update.execute();
					
					// Clean up
					update.close();
					
					player.sendMessage(ChatColor.GREEN + "Password updated successfully");
				} else { // oldpasswd does not match current password
					player.sendMessage(ChatColor.YELLOW + "Incorrect password");
				}
			}
			
			result.close();
		} catch (SQLException se) {
			se.printStackTrace();
		} catch (UnsupportedEncodingException uee) {
			uee.printStackTrace();
		} catch (NoSuchAlgorithmException nsae) {
			nsae.printStackTrace();
		}
	}

	public void tryResetPassword(CommandSender sender, String[] input) {
		String username = input[0];
		String passwd = input[1];
		
		try {
			// Create connection and exect SELECT query
			Connection conn = plugin.connectionmanager.getConnection();
			
			String query = "SELECT * FROM `" + login + "` WHERE `username`='" + username + "'";
			Statement statement = conn.createStatement();
			ResultSet result = statement.executeQuery(query);
			
			if (result.next()) { // Data found
				// Generate new password
				byte[] salt = HashManager.GenerateSalt();
				byte[] pass = HashManager.GenerateHash(passwd, salt);
				
				// Create update query
				String queryUpdate = "UPDATE `"
						+ login
						+ "` SET `pwreset`= ?, `temppwd`= ?, `tmpsalt`= ? WHERE `username`= ?";
				
				PreparedStatement update = conn.prepareStatement(queryUpdate);
				
				// Set values
				update.setInt(1, 1);
				update.setBytes(2, pass);
				update.setBytes(3, salt);
				update.setString(4, username);
				
				// Execute the query
				update.execute();
				
				// Clean up
				update.close();
				
				// Send feedback to player
				if (sender instanceof Player) {
					sender.sendMessage(ChatColor.YELLOW
							+ "Password for "
							+ username
							+ " has been reset; Inform them of their new temp password");
				} else {
					plugin.log.println("Password for "
							+ username
							+ " has been reset; Inform them of their new temp password");
				}
			}
			
			result.close();
			statement.close();
		} catch (SQLException se) {
			se.printStackTrace();
		} catch (NoSuchAlgorithmException nsae) {
			nsae.printStackTrace();
		} catch (UnsupportedEncodingException uee) {
			uee.printStackTrace();
		}
	}

	public void tryJumblePassword(CommandSender sender, String[] split) {
		String username = split[0];
		
		try {
			Connection conn = plugin.connectionmanager.getConnection();
			Statement select = conn.createStatement();
			ResultSet result = select.executeQuery(
					"SELECT * FROM `" + login + "` WHERE `username`='" + username + "'");
			
			boolean playerFound = false;
			if (result.next()) { // Results found
				do {
					String name = result.getString(1);
					if (name.equals(username)) { // Player data found
						playerFound = true;
					}
				} while (result.next());
			}
			
			if (playerFound == true) {
				byte[] rpasswd = HashManager.GenerateHash(
						HashManager.RandomString(), HashManager.GenerateSalt());
				Statement update = conn.createStatement();
				String query = "UPDATE `" + login
						+ "` SET `password`='" + rpasswd
						+ "' WHERE `username`='" + username + "'";
				update.executeUpdate(query);
				update.close();
				
				if (sender instanceof Player) {
					sender.sendMessage(ChatColor.YELLOW +
							"Password for " + username + " has been replaced");
				} else {
					plugin.log.println(
							"Password for " + username + " has been replaced");
				}
			}
			else if (sender instanceof Player) {
				sender.sendMessage(ChatColor.YELLOW + 
						"Player " + username + " not found");
			} else {
				plugin.log.println(
						"Player " + username + " not found");
			}
			result.close();
			select.close();
		} catch (SQLException se) {
			se.printStackTrace();
		} catch (NoSuchAlgorithmException nsae) {
			nsae.printStackTrace();
		} catch (UnsupportedEncodingException uee) {
			uee.printStackTrace();
		}
	}

	public long getRegistrationDate(Player player) {
		String name = player.getName();
		long regdate = -1;
		
		try {
			Connection conn = plugin.connectionmanager.getConnection();
			Statement select = conn.createStatement();
			ResultSet result = select.executeQuery(
					"SELECT `dateregistered` FROM `" + login + "` WHERE `username`='" + name + "'");
			
			if (result.next()) { // player found
				do {
					regdate = result.getLong(1);
				} while (result.next());
			}
		} catch (SQLException se) {
			se.printStackTrace();
		}
		
		return regdate;
	}
}
