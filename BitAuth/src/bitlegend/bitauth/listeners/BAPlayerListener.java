package bitlegend.bitauth.listeners;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;

import bitlegend.bitauth.BitAuth;

public class BAPlayerListener implements Listener {
	private BitAuth instance;
	
	// Database info
	private String user = "";
	private String pass = "";
	private String url = "";
	
	public BAPlayerListener(BitAuth instance) {
		this.instance = instance;
		user = instance.config.readString("DB_User");
		pass = instance.config.readString("DB_Pass");
		url = "jdbc:mysql://" + instance.config.readString("DB_Host") + 
				"/" + instance.config.readString("DB_Name");
	}
	
	@EventHandler (priority = EventPriority.HIGHEST)
	public void onPlayerLogin(PlayerLoginEvent event) {
		boolean whitelisted = false; // Will be false if player doesn't exist in database
		try {
			Connection conn = DriverManager.getConnection(url, user, pass);
			Statement select = conn.createStatement();
			ResultSet result = select.executeQuery(
					"SELECT * FROM `bit_whitelist`");
			while (result.next()) {
				String user = result.getString(1);
				boolean allowed = (result.getInt(2) == 1 ? true : false);
				if (user.equals(event.getPlayer().getDisplayName())) // Player found
					if (allowed == true)
						whitelisted = true;
			}
			result.close();
			select.close();
			conn.close();
		} catch (SQLException sq) {
			sq.printStackTrace();
		}
		
		if (whitelisted == false)
			event.disallow(null, "You are not whitelisted.");
		else {
			try {
				Player player = event.getPlayer();
				Connection conn = DriverManager.getConnection(url, user, pass);
				Statement select = conn.createStatement();
				ResultSet result = select.executeQuery(
						"SELECT * FROM `bit_login`");
				if (result.next()) { // Results found
					boolean playerFound = false;
					do {
						String username = result.getString(1);
						if (username.equals(player.getDisplayName())) { // Player
																		// data
																		// found
							playerFound = true;
							long lastlogintest = (System.currentTimeMillis() / 1000L) - 1800;
							long lastlogintime = result.getLong(5);
							boolean playerLoggedIn = false;
	
							if (lastlogintime < lastlogintest) {
								instance.requireLogin.add(player);
								playerLoggedIn = false;
							} else {
								playerLoggedIn = true;
							}
							
							if (playerLoggedIn == true) {
								boolean alreadyInList = false;
								boolean alreadyInList2 = false;
								int index = 0;
								for (Player p : instance.loggedIn) {
									if (p.getDisplayName().equals(player.getDisplayName()))
										alreadyInList = true;
								}
								for (Player p : instance.requireLogin) {
									if (p.getDisplayName().equals(player.getDisplayName())) {
										alreadyInList2 = true;
										index = instance.requireLogin.indexOf(p);
									}
								}
								if (alreadyInList == false)
									instance.loggedIn.add(player);
								if (alreadyInList2 == true)
									instance.requireLogin.remove(index);
								player.sendMessage(ChatColor.GREEN +
										"Welcome back, " + player.getDisplayName());
							}
						}
					} while (result.next());
					if (playerFound == false) { // Player data not found
						instance.unregistered.add(player);
					}
				}
				else { // No records found, create new for this player
					instance.unregistered.add(player);
				}
				result.close();
				select.close();
				conn.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		for (Player p : instance.unregistered) {
			if (p.getDisplayName().equals(event.getPlayer().getDisplayName())) {
				p.sendMessage(new String[] { 
						ChatColor.GREEN + "Oh no! It appears you haven't registered for our server.", 
						ChatColor.GREEN + "Not to fret though, you can register by simply typing:",
						ChatColor.GREEN + "/register <password>" 
				});
			}
		}
		for (Player p : instance.requireLogin) {
			if (p.getDisplayName().equals(event.getPlayer().getDisplayName())) {
				p.sendMessage(new String[] { 
						ChatColor.GREEN + "Please log in by typing:",
						ChatColor.GREEN + "/login <password>" });
			}
		}
		for (Player p : instance.loggedIn) {
			if (p.getDisplayName().equals(event.getPlayer().getDisplayName())) {
				p.sendMessage(ChatColor.GREEN + 
						"Welcome back, " + event.getPlayer().getDisplayName());
			}
		}
	}
	
	@EventHandler
	public void onPlayerMove(PlayerMoveEvent event) {
		for (Player p : instance.unregistered) {
			if (p.getDisplayName().equals(event.getPlayer().getDisplayName())) {
				event.setCancelled(true);
			}
		}
		for (Player p : instance.requireLogin) {
			if (p.getDisplayName().equals(event.getPlayer().getDisplayName())) {
				event.setCancelled(true);
			}
		}
	}
	
	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		int[] index = new int[instance.unregistered.size()], 
				index2 = new int[instance.requireLogin.size()], 
				index3 = new int[instance.loggedIn.size()];
		Player player = event.getPlayer();
		
		for (int ti = 0; ti < index.length; ti++)
			index[ti] = -1;
		for (int ti = 0; ti < index2.length; ti++)
			index2[ti] = -1;
		for (int ti = 0; ti < index3.length; ti++)
			index3[ti] = -1;
		
		int i = 0, j = 0, k = 0;
		for (Player p : instance.unregistered) {
			if (p.getDisplayName().equals(player.getDisplayName())) {
				index[i] = instance.unregistered.indexOf(p);
				i++;
			}
		}
		for (Player p : instance.requireLogin) {
			if (p.getDisplayName().equals(player.getDisplayName())) {
				index2[j] = instance.requireLogin.indexOf(p);
				j++;
			}
		}
		for (Player p : instance.loggedIn) {
			if (p.getDisplayName().equals(player.getDisplayName())) {
				index3[k] = instance.loggedIn.indexOf(p);
				k++;
			}
		}
		
		boolean remove = false, remove2 = false, remove3 = false;
		for (int ii = 0; ii < index.length; ii++) {
			if (index[ii] > -1)
				remove = true;
			if (remove == true)
				instance.unregistered.remove(ii);
		}
		for (int ii = 0; ii < index2.length; ii++) {
			if (index2[ii] > -1)
				remove2 = true;
			if (remove2 == true)
				instance.requireLogin.remove(ii);
		}
		for (int ii = 0; ii < index3.length; ii++) {
			if (index3[ii] > -1)
				remove3 = true;
			if (remove3 == true)
				instance.loggedIn.remove(ii);
		}
	}
	
	@EventHandler
	public void onPlayerPickupItem(PlayerPickupItemEvent event) {
		for (Player p : instance.unregistered) {
			if (p.getDisplayName().equals(event.getPlayer().getDisplayName())) {
				event.setCancelled(true);
			}
		}
		for (Player p : instance.requireLogin) {
			if (p.getDisplayName().equals(event.getPlayer().getDisplayName())) {
				event.setCancelled(true);
			}
		}
	}
	
	@EventHandler
	public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
		String[] split = event.getMessage().split(" ");
		boolean allow = false;
		
		if (split[0].equals("/register"))
			allow = true;
		if (split[0].equals("/login"))
			allow = true;
		
		if (allow == false) {
			for (Player p : instance.unregistered) {
				if (p.getDisplayName().equals(event.getPlayer().getDisplayName())) {
					event.setCancelled(true);
				}
			}
			for (Player p : instance.requireLogin) {
				if (p.getDisplayName().equals(event.getPlayer().getDisplayName())) {
					event.setCancelled(true);
				}
			}
		}
	}
	
	@EventHandler
	public void onPlayerChat(PlayerChatEvent event) {
		for (Player p : instance.unregistered) {
			if (p.getDisplayName().equals(event.getPlayer().getDisplayName())) {
				event.setCancelled(true);
			}
		}
		for (Player p : instance.requireLogin) {
			if (p.getDisplayName().equals(event.getPlayer().getDisplayName())) {
				event.setCancelled(true);
			}
		}
	}
	
	@EventHandler
	public void onPlayerInteractEvent(PlayerInteractEvent event) {
		for (Player p : instance.unregistered) {
			if (p.getDisplayName().equals(event.getPlayer().getDisplayName())) {
				event.setCancelled(true);
			}
		}
		for (Player p : instance.requireLogin) {
			if (p.getDisplayName().equals(event.getPlayer().getDisplayName())) {
				event.setCancelled(true);
			}
		}
	}
	
	@EventHandler
	public void onPlayerInteractEntityEvent(PlayerInteractEntityEvent event) {
		for (Player p : instance.unregistered) {
			if (p.getDisplayName().equals(event.getPlayer().getDisplayName())) {
				event.setCancelled(true);
			}
		}
		for (Player p : instance.requireLogin) {
			if (p.getDisplayName().equals(event.getPlayer().getDisplayName())) {
				event.setCancelled(true);
			}
		}
	}

	@EventHandler
	public void onPlayerExpChangeEvent(PlayerExpChangeEvent event) {
		for (Player p : instance.unregistered) {
			if (p.getDisplayName().equals(event.getPlayer().getDisplayName())) {
				event.setAmount(0);
			}
		}
		for (Player p : instance.requireLogin) {
			if (p.getDisplayName().equals(event.getPlayer().getDisplayName())) {
				event.setAmount(0);
			}
		}
	}
	
	@EventHandler
	public void onPlayerToggleSneakEvent(PlayerToggleSneakEvent event) {
		for (Player p : instance.unregistered) {
			if (p.getDisplayName().equals(event.getPlayer().getDisplayName())) {
				event.setCancelled(true);
			}
		}
		for (Player p : instance.requireLogin) {
			if (p.getDisplayName().equals(event.getPlayer().getDisplayName())) {
				event.setCancelled(true);
			}
		}
	}
	
	@EventHandler
	public void onPlayerToggleSprintEvent(PlayerToggleSprintEvent event) {
		for (Player p : instance.unregistered) {
			if (p.getDisplayName().equals(event.getPlayer().getDisplayName())) {
				event.setCancelled(true);
			}
		}
		for (Player p : instance.requireLogin) {
			if (p.getDisplayName().equals(event.getPlayer().getDisplayName())) {
				event.setCancelled(true);
			}
		}
	}
	
	@EventHandler
	public void onPlayerDropItemEvent(PlayerDropItemEvent event) {
		for (Player p : instance.unregistered) {
			if (p.getDisplayName().equals(event.getPlayer().getDisplayName())) {
				event.setCancelled(true);
			}
		}
		for (Player p : instance.requireLogin) {
			if (p.getDisplayName().equals(event.getPlayer().getDisplayName())) {
				event.setCancelled(true);
			}
		}
	}
}
