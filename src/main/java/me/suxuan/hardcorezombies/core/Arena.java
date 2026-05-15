package me.suxuan.hardcorezombies.core;

import me.suxuan.hardcorezombies.weapon.WeaponFactory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Arena {

	private final String arenaId;
	private final String templateName;
	private World world; // ASWM 生成的临时世界
	private GameState state;

	private final WaveManager waveManager;
	private final Map<UUID, GamePlayer> players;

	private int currentWave;

	public Arena(String arenaId, String templateName) {
		this.arenaId = arenaId;
		this.templateName = templateName;
		this.state = GameState.WAITING;
		this.players = new ConcurrentHashMap<>();
		this.currentWave = 0;
		this.waveManager = new WaveManager(this);
	}

	/**
	 * 将 ASWM 加载好的世界实例绑定到此房间
	 */
	public void setWorld(World world) {
		this.world = world;
	}

	public World getWorld() {
		return world;
	}

	public String getArenaId() {
		return arenaId;
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

	/**
	 * 玩家加入房间
	 */
	public boolean addPlayer(Player player) {
		if (state != GameState.WAITING && state != GameState.STARTING) {
			player.sendMessage(Component.text("该房间游戏已开始，无法加入！", NamedTextColor.RED));
			return false;
		}

		// 【核心修改】实例化该玩家的局内数据
		GamePlayer gamePlayer = new GamePlayer(player, this);
		players.put(player.getUniqueId(), gamePlayer);

		if (world != null) {
			player.teleportAsync(world.getSpawnLocation());
		}

		broadcast(Component.text(player.getName() + " 加入了游戏! (" + players.size() + "/4)", NamedTextColor.GRAY));
		return true;
	}

	/**
	 * 玩家离开房间
	 */
	public void removePlayer(Player player) {
		players.remove(player.getUniqueId());
		// TODO: 清理玩家背包，将其传送回大厅

		if (players.isEmpty() && state == GameState.IN_GAME) {
			endGame(false);
		}
	}

	/**
	 * 获取玩家的局内数据
	 */
	public GamePlayer getGamePlayer(Player player) {
		return players.get(player.getUniqueId());
	}

	/**
	 * 判断玩家是否在此房间中
	 */
	public boolean hasPlayer(Player player) {
		return players.containsKey(player.getUniqueId());
	}

	/**
	 * 获取房间内所有的局内玩家数据
	 */
	public Collection<GamePlayer> getGamePlayers() {
		return players.values();
	}

	/**
	 * 正式开始游戏
	 */
	public void startGame() {
		if (this.state != GameState.WAITING && this.state != GameState.STARTING) return;

		this.state = GameState.IN_GAME;
		broadcastTitle(
				Component.text("游戏开始", NamedTextColor.RED),
				Component.text("活下去...", NamedTextColor.GRAY)
		);
		for (GamePlayer gamePlayer : players.values()) {
			gamePlayer.getPlayer().give(WeaponFactory.createDesertEagle());
			gamePlayer.getPlayer().give(WeaponFactory.createM4A1());
			gamePlayer.getPlayer().give(WeaponFactory.createS686());
		}

		this.waveManager.startNextWave();
	}

	/**
	 * 广播 Component 消息给房间内所有玩家
	 */
	public void broadcast(Component message) {
		for (GamePlayer gamePlayer : players.values()) {
			Player player = gamePlayer.getPlayer();
			if (player != null && player.isOnline()) {
				player.sendMessage(message);
			}
		}
	}

	/**
	 * 发送 Title 给房间内所有玩家
	 */
	public void broadcastTitle(Component mainTitle, Component subTitle) {
		Title title = Title.title(mainTitle, subTitle);
		for (GamePlayer gamePlayer : players.values()) {
			Player player = gamePlayer.getPlayer();
			if (player != null && player.isOnline()) {
				player.showTitle(title);
			}
		}
	}

	/**
	 * 结束游戏逻辑
	 *
	 * @param victory 是否通关
	 */
	public void endGame(boolean victory) {
		this.state = GameState.ENDING;

		Component resultMsg = victory ?
				Component.text("生还成功！", NamedTextColor.GREEN) :
				Component.text("全军覆没...", NamedTextColor.RED);

		broadcastTitle(resultMsg, Component.text("存活波次: " + currentWave, NamedTextColor.GOLD));

		// TODO: 计算奖励，延迟几秒后踢出所有玩家，并通知 ArenaManager 卸载世界
	}

}