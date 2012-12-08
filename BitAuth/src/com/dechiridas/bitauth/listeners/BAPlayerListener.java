package com.dechiridas.bitauth.listeners;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.event.player.PlayerLoginEvent.Result;
import com.dechiridas.bitauth.BitAuth;
import com.dechiridas.bitauth.player.BAPlayer;
import com.dechiridas.bitauth.player.BAState;

public class BAPlayerListener implements Listener {
	private BitAuth plugin;
	
	public BAPlayerListener(BitAuth instance) {
		this.plugin = instance;
	}
	
	@EventHandler
	public void onPlayerLogin(PlayerLoginEvent event) {
		Player player = event.getPlayer();
		boolean whitelisted = plugin.database.isWhitelisted(event.getPlayer());
		long regdate = plugin.database.getRegistrationDate(event.getPlayer());
		long now = (long)(Math.floor(System.currentTimeMillis() / 1000));
		long diff = now - regdate;
		
		if ((whitelisted == false || diff < plugin.database.getWhitelistBypassTime()) 
				&& plugin.database.whitelistEnabled() == true)
			event.disallow(Result.KICK_WHITELIST, "You are not whitelisted.");
		else if (!player.getName().matches("(?i)[a-z0-9_]{1,16}"))
			event.disallow(Result.KICK_OTHER, "Invalid username! Must only contain a-Z, 0-9 and _ and be 1-16 characters long!");
		else
			plugin.database.tryLogin(event.getPlayer(), event);
	}
	
	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		BAPlayer ba = null;
		BAState state = null;
		long timeout = System.nanoTime();
		
		/* Some debug: could it be that onPlayerJoin fires before onPlayerLogin is
		 * finished sometimes? I left some debug code in PlayerManager.java to
		 * try to pin down the problem.
		 */
		while (true) {
			ba = plugin.pman.getBAPlayerByName(player.getName());
			
			if (ba == null) {
				try {
					Thread.sleep(100L);
				} catch (InterruptedException e) {}
			} else {
				break;
			}
			
			// Exits the loop if it takes more than 2 seconds.
			if (System.nanoTime() >= timeout + 2000000000L) {
				player.kickPlayer("Something went wrong with authentication. Please relog.");
				plugin.log.println(ChatColor.stripColor(player.getDisplayName()) + " was kicked because BitAuth fucked up.");
				break;
			}
		}
		
		state = ba.getState();
		
		boolean pwreset = false;

		plugin.database.offsetPlayerIPHistory(player);

		if (state == BAState.LOGGEDIN) {
			player.sendMessage(ChatColor.GREEN + 
					"Welcome back, " + event.getPlayer().getName());
		} else if (state == BAState.LOGGEDOUT) {
			/* Inventory playerInventory = player.getInventory();
			ItemStack[] contents = playerInventory.getContents();
			BAPlayer.inventories.put(player, contents);
			playerInventory.clear();
			player.sendMessage("Inventory cleared"); */
			
			player.sendMessage(new String[] { 
					ChatColor.GREEN + "Please log in by typing:",
					ChatColor.GREEN + "/login <password>"
			});
		} else if (state == BAState.REGISTERED) {
			
		} else if (state == BAState.UNREGISTERED) {
			player.sendMessage(new String[] { 
					ChatColor.GREEN + "Oh no! It appears you haven't registered for our server.", 
					ChatColor.GREEN + "Not to fret though, you can register by simply typing:",
					ChatColor.GREEN + "/register <password>" 
			});
		} else if (state == BAState.PWRESET) {
			player.sendMessage(new String[] { 
					ChatColor.RED + "You need to reset your password! You can do this by typing:",
					ChatColor.RED + "/login <temp password> <new password>"
			});
			pwreset = true;
		}
		
		if (pwreset == true)
			player.sendMessage(new String[] { 
					ChatColor.GREEN + "Please log in by typing:",
					ChatColor.GREEN + "/login <password>"
			});
	}

	@EventHandler
	public void onPlayerMove(PlayerMoveEvent event) {
		Player player = event.getPlayer();
		BAPlayer ba = plugin.pman.getBAPlayerByName(player.getName());
		BAState state = ba.getState();
		
		if (state == BAState.UNREGISTERED || state == BAState.LOGGEDOUT)
			event.setCancelled(true);
	}
	
	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		BAPlayer ba = plugin.pman.getBAPlayerByName(player.getName());
		/* This doesn't work, I just wanted to see what happened
		
		BAState state = ba.getState();
		
		if (state == BAState.UNREGISTERED || state == BAState.LOGGEDOUT) {
			OfflinePlayer offlinePlayer = Bukkit.getServer().getOfflinePlayer(player.getName());
			PlayerInventory playerInventory = offlinePlayer.getInventory();
			ItemStack[] contents = BAPlayer.inventories.get(player);
			playerInventory.setContents(contents);
			// player.sendMessage("Inventory restored");
		} */

		plugin.pman.removePlayer(ba);
	}
	
	@EventHandler
	public void onPlayerPickupItem(PlayerPickupItemEvent event) {
		Player player = event.getPlayer();
		BAPlayer ba = plugin.pman.getBAPlayerByName(player.getName());
		BAState state = ba.getState();

		if (state == BAState.UNREGISTERED || state == BAState.LOGGEDOUT)
			event.setCancelled(true);
	}
	
	@EventHandler
	public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
		Player player = event.getPlayer();
		BAPlayer ba = plugin.pman.getBAPlayerByName(player.getName());
		BAState state = ba.getState();
		
		String[] split = event.getMessage().split(" ");
		boolean allow = false;
		
		if (split[0].equals("/register"))
			allow = true;
		if (split[0].equals("/login"))
			allow = true;
		
		if (allow == false)
			if (state == BAState.UNREGISTERED || state == BAState.LOGGEDOUT)
				event.setCancelled(true);
	}
	
	// PlayerChatEvent deprecated, replaced with AsyncPlayerChatEvent
	@EventHandler
	public void onPlayerChat(AsyncPlayerChatEvent event) {
		Player player = event.getPlayer();
		BAPlayer ba = plugin.pman.getBAPlayerByName(player.getName());
		BAState state = ba.getState();
		
		if (state == BAState.UNREGISTERED || state == BAState.LOGGEDOUT)
			event.setCancelled(true);
	}
	
	@EventHandler (priority = EventPriority.LOWEST)
	public void onPlayerInteract(PlayerInteractEvent event) {
		Player player = event.getPlayer();
		BAPlayer ba = plugin.pman.getBAPlayerByName(player.getName());
		BAState state = ba.getState();

		if (state == BAState.UNREGISTERED || state == BAState.LOGGEDOUT) {
			event.setCancelled(true);
			player.sendMessage("PlayerInteractEvent cancelled - did Essentials be a dick?");
		}
	}
	
	@EventHandler
	public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
		Player player = event.getPlayer();
		BAPlayer ba = plugin.pman.getBAPlayerByName(player.getName());
		BAState state = ba.getState();

		if (state == BAState.UNREGISTERED || state == BAState.LOGGEDOUT)
			event.setCancelled(true);
	}
	
	@EventHandler
	public void onPlayerExpChange(PlayerExpChangeEvent event) {
		Player player = event.getPlayer();
		BAPlayer ba = plugin.pman.getBAPlayerByName(player.getName());
		BAState state = ba.getState();

		if (state == BAState.UNREGISTERED || state == BAState.LOGGEDOUT)
			event.setAmount(0);
	}
	
	@EventHandler
	public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
		Player player = event.getPlayer();
		BAPlayer ba = plugin.pman.getBAPlayerByName(player.getName());
		BAState state = ba.getState();
		
		if (state == BAState.UNREGISTERED || state == BAState.LOGGEDOUT)
			event.setCancelled(true);
	}
	
	@EventHandler
	public void onPlayerToggleSprint(PlayerToggleSprintEvent event) {
		Player player = event.getPlayer();
		BAPlayer ba = plugin.pman.getBAPlayerByName(player.getName());
		BAState state = ba.getState();
		
		if (state == BAState.UNREGISTERED || state == BAState.LOGGEDOUT)
			event.setCancelled(true);
	}
	
	@EventHandler
	public void onPlayerDropItem(PlayerDropItemEvent event) {
		Player player = event.getPlayer();
		BAPlayer ba = plugin.pman.getBAPlayerByName(player.getName());
		BAState state = ba.getState();
		
		if (state == BAState.UNREGISTERED || state == BAState.LOGGEDOUT)
			event.setCancelled(true);
	}
}
