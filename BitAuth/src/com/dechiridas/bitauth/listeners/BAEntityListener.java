package com.dechiridas.bitauth.listeners;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;

import com.dechiridas.bitauth.BitAuth;
import com.dechiridas.bitauth.player.BAPlayer;
import com.dechiridas.bitauth.player.BAState;

public class BAEntityListener implements Listener {
	private BitAuth plugin;
	
	public BAEntityListener(BitAuth instance) {
		this.plugin = instance;
	}
	
	@EventHandler
	public void onEntityDamage(EntityDamageEvent event) {
		Entity entity = event.getEntity();
		if (entity instanceof Player) {
			Player player = (Player)entity;
			BAPlayer ba = plugin.pman.getBAPlayerByName(player.getName());
			BAState state = ba.getState();
			
			if (state == BAState.LOGGEDOUT || state == BAState.UNREGISTERED)
				event.setCancelled(true);
		}
	}
	
	@EventHandler
	public void onEntityTarget(EntityTargetEvent event) {
		Entity target = event.getTarget();
		if (target instanceof Player) {
			Player player = (Player)target;
			BAPlayer ba = plugin.pman.getBAPlayerByName(player.getName());
			BAState state = ba.getState();
			
			if (state == BAState.LOGGEDOUT || state == BAState.UNREGISTERED)
				event.setCancelled(true);
		}
	}
}
