package me.suxuan.hardcorezombies.gameplay;

import me.suxuan.hardcorezombies.core.Arena;
import me.suxuan.hardcorezombies.core.GamePlayer;
import me.suxuan.hardcorezombies.core.GameRoomManager;
import me.suxuan.hardcorezombies.core.GameState;
import org.bukkit.entity.Player;

/**
 * 对局内玩家互相之间无碰撞（仍可与僵尸等实体碰撞）。
 */
public class PlayerCollisionManager {

	private final GameRoomManager roomManager;

	public PlayerCollisionManager(GameRoomManager roomManager) {
		this.roomManager = roomManager;
	}

	public void applyArena(Arena arena) {
		if (arena.getState() != GameState.IN_GAME) return;
		for (GamePlayer gp : arena.getGamePlayers()) {
			Player player = gp.getPlayer();
			if (player != null && player.isOnline()) {
				applyNoPlayerCollision(player, arena);
			}
		}
	}

	public void refreshPlayer(Player player) {
		if (player == null || !player.isOnline()) return;

		Arena arena = roomManager.getPlayerArena(player);
		if (arena != null && arena.getState() == GameState.IN_GAME) {
			applyNoPlayerCollision(player, arena);
		} else {
			clearPlayerCollision(player);
		}
	}

	public void removePlayerFromArena(Player leaving, Arena arena) {
		if (leaving == null) return;

		clearPlayerCollision(leaving);

		for (GamePlayer gp : arena.getGamePlayers()) {
			Player other = gp.getPlayer();
			if (other == null || other.equals(leaving)) continue;
			other.getCollidableExemptions().remove(leaving.getUniqueId());
		}
	}

	private void applyNoPlayerCollision(Player player, Arena arena) {
		player.setCollidable(true);

		for (GamePlayer gp : arena.getGamePlayers()) {
			Player other = gp.getPlayer();
			if (other == null || other.equals(player) || !other.isOnline()) continue;

			player.getCollidableExemptions().add(other.getUniqueId());
			other.getCollidableExemptions().add(player.getUniqueId());
		}
	}

	private void clearPlayerCollision(Player player) {
		player.setCollidable(true);
		player.getCollidableExemptions().clear();
	}
}
