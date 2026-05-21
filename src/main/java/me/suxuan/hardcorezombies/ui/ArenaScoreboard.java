package me.suxuan.hardcorezombies.ui;

import me.suxuan.hardcorezombies.HardcoreZombies;
import me.suxuan.hardcorezombies.core.Arena;
import me.suxuan.hardcorezombies.core.GamePlayer;
import me.suxuan.hardcorezombies.core.GameState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ArenaScoreboard {

	private final Arena arena;
	private final Map<UUID, Scoreboard> playerBoards = new HashMap<>();
	private BukkitTask updateTask;

	public ArenaScoreboard(Arena arena) {
		this.arena = arena;
	}

	public void addPlayer(Player player) {
		Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
		Objective objective = board.registerNewObjective(
				"hz_" + arena.getArenaId(),
				Criteria.DUMMY,
				Component.text("僵尸末日", NamedTextColor.RED)
		);
		objective.setDisplaySlot(DisplaySlot.SIDEBAR);
		player.setScoreboard(board);
		playerBoards.put(player.getUniqueId(), board);
		startUpdaterIfNeeded();
	}

	public void removePlayer(Player player) {
		playerBoards.remove(player.getUniqueId());
		player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
	}

	public void shutdown() {
		if (updateTask != null) {
			updateTask.cancel();
			updateTask = null;
		}
		for (UUID uuid : playerBoards.keySet()) {
			Player player = Bukkit.getPlayer(uuid);
			if (player != null) {
				player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
			}
		}
		playerBoards.clear();
	}

	private void startUpdaterIfNeeded() {
		if (updateTask != null) return;

		updateTask = new BukkitRunnable() {
			@Override
			public void run() {
				if (playerBoards.isEmpty()) {
					cancel();
					updateTask = null;
					return;
				}
				refreshAll();
			}
		}.runTaskTimer(HardcoreZombies.getInstance(), 0L, 20L);
	}

	private void refreshAll() {
		int alive = 0;
		for (GamePlayer gp : arena.getGamePlayers()) {
			if (!gp.isDead()) alive++;
		}

		for (Map.Entry<UUID, Scoreboard> entry : playerBoards.entrySet()) {
			Player player = Bukkit.getPlayer(entry.getKey());
			if (player == null || !player.isOnline()) continue;

			GamePlayer gp = arena.getGamePlayer(player);
			if (gp == null) continue;

			Scoreboard board = entry.getValue();
			Objective obj = board.getObjective("hz_" + arena.getArenaId());
			if (obj == null) continue;

			for (String line : board.getEntries()) {
				board.resetScores(line);
			}

			int line = 8;
			setLine(obj, line--, "§7房间: §f" + arena.getArenaId());
			setLine(obj, line--, "§7状态: §f" + formatState(arena.getState()));
			setLine(obj, line--, "§7波次: §c" + arena.getCurrentWave());
			setLine(obj, line--, "§7存活: §a" + alive + "/" + arena.getGamePlayers().size());
			setLine(obj, line--, " ");
			setLine(obj, line--, "§6金币: §e" + gp.getCoins());
			setLine(obj, line--, "§7击杀: §f" + gp.getKills());

			String status;
			if (gp.isDead()) {
				status = "§8已阵亡";
			} else if (gp.isDowned()) {
				status = "§c倒地";
			} else {
				status = "§a作战中";
			}
			setLine(obj, line, "§7状态: " + status);
		}
	}

	private static void setLine(Objective objective, int score, String text) {
		objective.getScore(text).setScore(score);
	}

	private static String formatState(GameState state) {
		return switch (state) {
			case WAITING -> "等待";
			case STARTING -> "倒计时";
			case IN_GAME -> "进行中";
			case ENDING -> "结算";
		};
	}
}
