package me.suxuan.hardcorezombies.core;

import me.suxuan.hardcorezombies.HardcoreZombies;
import me.suxuan.hardcorezombies.config.PluginConfig;
import me.suxuan.hardcorezombies.ui.ArenaScoreboard;
import me.suxuan.hardcorezombies.util.PlayerResetUtil;
import me.suxuan.hardcorezombies.weapon.WeaponFactory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Arena {

	private final String arenaId;
	private final String templateName;
	private final GameRoomManager roomManager;

	private World world;
	private GameState state;

	private final WaveManager waveManager;
	private final Map<UUID, GamePlayer> players;
	private final ArenaScoreboard scoreboard;

	private BukkitTask countdownTask;
	private BukkitTask endTask;
	private boolean ending;

	public Arena(String arenaId, String templateName, GameRoomManager roomManager) {
		this.arenaId = arenaId;
		this.templateName = templateName;
		this.roomManager = roomManager;
		this.state = GameState.WAITING;
		this.players = new ConcurrentHashMap<>();
		this.waveManager = new WaveManager(this);
		this.scoreboard = new ArenaScoreboard(this);
	}

	public void setWorld(World world) {
		this.world = world;
		for (GamePlayer gamePlayer : players.values()) {
			Player player = gamePlayer.getPlayer();
			if (player != null && player.isOnline()) {
				player.teleportAsync(world.getSpawnLocation());
			}
		}
	}

	public World getWorld() {
		return world;
	}

	public String getArenaId() {
		return arenaId;
	}

	public String getTemplateName() {
		return templateName;
	}

	public GameState getState() {
		return state;
	}

	public void setState(GameState state) {
		this.state = state;
	}

	public WaveManager getWaveManager() {
		return waveManager;
	}

	public ArenaScoreboard getScoreboard() {
		return scoreboard;
	}

	public int getCurrentWave() {
		return waveManager.getCurrentWave();
	}

	public boolean addPlayer(Player player) {
		PluginConfig config = HardcoreZombies.getInstance().getPluginConfig();

		if (state != GameState.WAITING && state != GameState.STARTING) {
			player.sendMessage(Component.text("该房间游戏已开始，无法加入！", NamedTextColor.RED));
			return false;
		}

		if (players.size() >= config.getMaxPlayers()) {
			player.sendMessage(Component.text("房间已满！", NamedTextColor.RED));
			return false;
		}

		Arena existing = roomManager.getPlayerArena(player);
		if (existing != null && existing != this) {
			existing.removePlayer(player, false);
		}

		GamePlayer gamePlayer = new GamePlayer(player, this);
		players.put(player.getUniqueId(), gamePlayer);

		if (world != null) {
			player.teleportAsync(world.getSpawnLocation());
		} else {
			player.sendMessage(Component.text("地图加载中，请稍候...", NamedTextColor.YELLOW));
		}

		scoreboard.addPlayer(player);
		broadcast(Component.text(
				player.getName() + " 加入了游戏! (" + players.size() + "/" + config.getMaxPlayers() + ")",
				NamedTextColor.GRAY
		));

		tryAutoStart();
		return true;
	}

	public void removePlayer(Player player, boolean notify) {
		GamePlayer gamePlayer = players.remove(player.getUniqueId());
		if (gamePlayer == null) return;

		roomManager.getCollisionManager().removePlayerFromArena(player, this);
		gamePlayer.cleanup();
		scoreboard.removePlayer(player);

		PluginConfig config = HardcoreZombies.getInstance().getPluginConfig();
		PlayerResetUtil.resetToLobby(player, config.getLobbyLocation());

		if (notify) {
			broadcast(Component.text(player.getName() + " 离开了游戏。", NamedTextColor.GRAY));
		}

		if (state == GameState.STARTING) {
			cancelCountdown();
			state = GameState.WAITING;
		}

		if (players.isEmpty()) {
			if (state == GameState.IN_GAME || state == GameState.ENDING) {
				endGame(false);
			} else if (state != GameState.ENDING) {
				roomManager.destroyRoom(arenaId);
			}
			return;
		}

		if (state == GameState.IN_GAME) {
			roomManager.getCollisionManager().applyArena(this);
			checkTeamWipe();
		}
	}

	public void removePlayer(Player player) {
		removePlayer(player, true);
	}

	public GamePlayer getGamePlayer(Player player) {
		return players.get(player.getUniqueId());
	}

	public boolean hasPlayer(Player player) {
		return players.containsKey(player.getUniqueId());
	}

	public Collection<GamePlayer> getGamePlayers() {
		return players.values();
	}

	public void tryAutoStart() {
		PluginConfig config = HardcoreZombies.getInstance().getPluginConfig();
		if (state != GameState.WAITING || world == null) return;
		if (players.size() < config.getMinPlayers()) return;
		startCountdown();
	}

	public void startCountdown() {
		if (state != GameState.WAITING) return;

		cancelCountdown();
		state = GameState.STARTING;

		PluginConfig config = HardcoreZombies.getInstance().getPluginConfig();
		final int[] secondsLeft = {config.getStartCountdownSeconds()};

		countdownTask = new BukkitRunnable() {
			@Override
			public void run() {
				if (state != GameState.STARTING) {
					cancel();
					return;
				}

				if (secondsLeft[0] <= 0) {
					startGame();
					cancel();
					return;
				}

				broadcastTitle(
						Component.text(String.valueOf(secondsLeft[0]), NamedTextColor.GOLD),
						Component.text("游戏即将开始", NamedTextColor.GRAY)
				);
				secondsLeft[0]--;
			}
		}.runTaskTimer(HardcoreZombies.getInstance(), 0L, 20L);
	}

	public void forceStart() {
		cancelCountdown();
		if (state == GameState.WAITING || state == GameState.STARTING) {
			startGame();
		}
	}

	public void startGame() {
		if (state != GameState.WAITING && state != GameState.STARTING) return;
		if (world == null) return;

		cancelCountdown();
		state = GameState.IN_GAME;
		ending = false;

		broadcastTitle(
				Component.text("游戏开始", NamedTextColor.RED),
				Component.text("活下去...", NamedTextColor.GRAY)
		);

		for (GamePlayer gamePlayer : players.values()) {
			Player player = gamePlayer.getPlayer();
			if (player == null || !player.isOnline()) continue;

			player.setGameMode(GameMode.SURVIVAL);
			player.getInventory().clear();
			player.give(WeaponFactory.createDesertEagle());
			player.give(WeaponFactory.createM4A1());
			player.give(WeaponFactory.createS686());
		}

		roomManager.getCollisionManager().applyArena(this);
		waveManager.startNextWave();
	}

	public void checkTeamWipe() {
		if (state != GameState.IN_GAME || ending) return;

		boolean anyoneAlive = false;
		for (GamePlayer gp : players.values()) {
			if (!gp.isDead()) {
				anyoneAlive = true;
				break;
			}
		}

		if (!anyoneAlive) {
			endGame(false);
		}
	}

	public void endGame(boolean victory) {
		if (ending) return;
		ending = true;
		state = GameState.ENDING;

		cancelCountdown();
		waveManager.cleanup();
		scoreboard.shutdown();

		PluginConfig config = HardcoreZombies.getInstance().getPluginConfig();
		int wave = waveManager.getCurrentWave();

		Component resultMsg = victory
				? Component.text("生还成功！", NamedTextColor.GREEN)
				: Component.text("全军覆没...", NamedTextColor.RED);

		broadcastTitle(resultMsg, Component.text("存活波次: " + wave, NamedTextColor.GOLD));

		for (GamePlayer gp : new ArrayList<>(players.values())) {
			int bonus = wave * config.getPerWaveSurvived()
					+ gp.getKills() * config.getPerKill();
			if (victory) {
				bonus += config.getVictoryBonus();
			}
			gp.addCoins(bonus);
			gp.getPlayer().sendMessage(Component.text(
					"结算奖励: +" + bonus + " 金币 (波次 " + wave + ", 击杀 " + gp.getKills() + ")",
					NamedTextColor.GOLD
			));
		}

		endTask = new BukkitRunnable() {
			@Override
			public void run() {
				List<Player> toRemove = new ArrayList<>();
				for (GamePlayer gp : players.values()) {
					Player player = gp.getPlayer();
					if (player != null && player.isOnline()) {
						toRemove.add(player);
					}
				}
				for (Player player : toRemove) {
					removePlayer(player, false);
				}
				roomManager.destroyRoom(arenaId);
			}
		}.runTaskLater(HardcoreZombies.getInstance(), config.getEndDelaySeconds() * 20L);
	}

	public void broadcast(Component message) {
		for (GamePlayer gamePlayer : players.values()) {
			Player player = gamePlayer.getPlayer();
			if (player != null && player.isOnline()) {
				player.sendMessage(message);
			}
		}
	}

	public void broadcastTitle(Component mainTitle, Component subTitle) {
		Title title = Title.title(mainTitle, subTitle);
		for (GamePlayer gamePlayer : players.values()) {
			Player player = gamePlayer.getPlayer();
			if (player != null && player.isOnline()) {
				player.showTitle(title);
			}
		}
	}

	private void cancelCountdown() {
		if (countdownTask != null) {
			countdownTask.cancel();
			countdownTask = null;
		}
	}

	public void shutdown() {
		cancelCountdown();
		if (endTask != null) {
			endTask.cancel();
		}
		waveManager.cleanup();
		scoreboard.shutdown();

		PluginConfig config = HardcoreZombies.getInstance().getPluginConfig();
		for (GamePlayer gp : new ArrayList<>(players.values())) {
			gp.cleanup();
			Player player = gp.getPlayer();
			if (player != null && player.isOnline()) {
				PlayerResetUtil.resetToLobby(player, config.getLobbyLocation());
			}
		}
		players.clear();
	}
}
