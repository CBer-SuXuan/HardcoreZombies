package me.suxuan.hardcorezombies.gameplay;

import me.suxuan.hardcorezombies.core.Arena;
import me.suxuan.hardcorezombies.core.GamePlayer;
import me.suxuan.hardcorezombies.core.GameRoomManager;
import me.suxuan.hardcorezombies.core.GameState;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashSet;
import java.util.Set;

/**
 * 对局内玩家互相之间无碰撞（仍可与僵尸等实体碰撞）。
 * Paper 1.21 上需使用玩家当前记分板的 Team.COLLISION_RULE，单靠 CollidableExemptions 不可靠。
 */
public class PlayerCollisionManager {

	private static final String TEAM_PREFIX = "hzc_";

	private final GameRoomManager roomManager;

	public PlayerCollisionManager(GameRoomManager roomManager) {
		this.roomManager = roomManager;
	}

	public void applyArena(Arena arena) {
		if (arena.getState() != GameState.IN_GAME) return;
		for (GamePlayer gp : arena.getGamePlayers()) {
			Player player = gp.getPlayer();
			if (player != null && player.isOnline()) {
				syncArenaCollisionTeam(player, arena);
			}
		}
	}

	public void refreshPlayer(Player player) {
		if (player == null || !player.isOnline()) return;

		Arena arena = roomManager.getPlayerArena(player);
		if (arena != null && arena.getState() == GameState.IN_GAME) {
			GamePlayer gp = arena.getGamePlayer(player);
			if (gp != null && gp.isDowned()) {
				applyDownedCollision(player, arena);
			} else {
				syncArenaCollisionTeam(player, arena);
			}
		} else {
			clearPlayerCollision(player);
		}
	}

	public void onPlayerDowned(Player player, Arena arena) {
		applyDownedCollision(player, arena);
		for (GamePlayer gp : arena.getGamePlayers()) {
			Player other = gp.getPlayer();
			if (other == null || other.equals(player) || !other.isOnline()) continue;
			syncArenaCollisionTeam(other, arena);
		}
	}

	public void removePlayerFromArena(Player leaving, Arena arena) {
		if (leaving == null) return;

		clearPlayerCollision(leaving);

		for (GamePlayer gp : arena.getGamePlayers()) {
			Player other = gp.getPlayer();
			if (other == null || other.equals(leaving) || !other.isOnline()) continue;
			syncArenaCollisionTeam(other, arena);
		}
	}

	private void syncArenaCollisionTeam(Player player, Arena arena) {
		player.setCollidable(true);
		player.getCollidableExemptions().clear();

		Scoreboard board = player.getScoreboard();
		Team team = getOrCreateCollisionTeam(board, arena.getArenaId());
		Set<String> memberNames = collectActiveMemberNames(arena);

		for (String entry : new HashSet<>(team.getEntries())) {
			if (!memberNames.contains(entry)) {
				team.removeEntry(entry);
			}
		}
		for (String name : memberNames) {
			if (!team.hasEntry(name)) {
				team.addEntry(name);
			}
		}
	}

	private void applyDownedCollision(Player player, Arena arena) {
		removePlayerFromCollisionTeam(player, arena.getArenaId());
		player.setCollidable(false);

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

		Arena arena = roomManager.getPlayerArena(player);
		if (arena != null) {
			removePlayerFromCollisionTeam(player, arena.getArenaId());
		}
	}

	private static Team getOrCreateCollisionTeam(Scoreboard board, String arenaId) {
		String teamName = teamNameForArena(arenaId);
		Team team = board.getTeam(teamName);
		if (team == null) {
			team = board.registerNewTeam(teamName);
			team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
		}
		return team;
	}

	private static void removePlayerFromCollisionTeam(Player player, String arenaId) {
		Team team = player.getScoreboard().getTeam(teamNameForArena(arenaId));
		if (team != null && team.hasEntry(player.getName())) {
			team.removeEntry(player.getName());
		}
	}

	private static Set<String> collectActiveMemberNames(Arena arena) {
		Set<String> names = new HashSet<>();
		for (GamePlayer gp : arena.getGamePlayers()) {
			Player player = gp.getPlayer();
			if (player != null && player.isOnline() && !gp.isDowned() && !gp.isDead()) {
				names.add(player.getName());
			}
		}
		return names;
	}

	/** 记分板 Team 名最长 16 字符 */
	private static String teamNameForArena(String arenaId) {
		String suffix = arenaId.length() > 12 ? arenaId.substring(arenaId.length() - 12) : arenaId;
		String name = TEAM_PREFIX + suffix;
		return name.length() > 16 ? name.substring(0, 16) : name;
	}
}
