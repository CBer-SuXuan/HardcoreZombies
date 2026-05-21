package me.suxuan.hardcorezombies.listener;

import me.suxuan.hardcorezombies.core.Arena;
import me.suxuan.hardcorezombies.core.GameRoomManager;
import me.suxuan.hardcorezombies.core.GameState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;

public class ArenaProtectionListener implements Listener {

	private final GameRoomManager roomManager;

	public ArenaProtectionListener(GameRoomManager roomManager) {
		this.roomManager = roomManager;
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent event) {
		if (shouldProtect(event.getPlayer())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBlockPlace(BlockPlaceEvent event) {
		if (shouldProtect(event.getPlayer())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBucketEmpty(PlayerBucketEmptyEvent event) {
		if (shouldProtect(event.getPlayer())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBucketFill(PlayerBucketFillEvent event) {
		if (shouldProtect(event.getPlayer())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onPickup(EntityPickupItemEvent event) {
		if (event.getEntity() instanceof Player player && shouldProtect(player)) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onHunger(FoodLevelChangeEvent event) {
		if (event.getEntity() instanceof Player player && shouldProtect(player)) {
			event.setCancelled(true);
		}
	}

	private boolean shouldProtect(Player player) {
		Arena arena = roomManager.getPlayerArena(player);
		if (arena == null) return false;
		if (arena.getState() != GameState.IN_GAME && arena.getState() != GameState.STARTING) {
			return false;
		}
		return roomManager.isInGameWorld(player);
	}
}
