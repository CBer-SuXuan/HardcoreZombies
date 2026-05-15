package me.suxuan.hardcorezombies.core;

import me.suxuan.hardcorezombies.HardcoreZombies;
import me.suxuan.slimearena.api.ArenaManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GameRoomManager {

	private final HardcoreZombies plugin;
	private final ArenaManager slimeArenaManager; // 您的 API

	// 存储当前正在运行的僵尸游戏房间
	private final Map<String, Arena> activeRooms = new ConcurrentHashMap<>();

	public GameRoomManager(HardcoreZombies plugin, ArenaManager slimeArenaManager) {
		this.plugin = plugin;
		this.slimeArenaManager = slimeArenaManager;
	}

	/**
	 * 创建一个新的游戏房间
	 */
	public void createRoom(String templateName) {
		String roomId = "hz_arena_" + UUID.randomUUID().toString().substring(0, 8);
		Arena arena = new Arena(roomId, templateName);
		activeRooms.put(roomId, arena);

		plugin.getComponentLogger().info(Component.text("正在向 SlimeArenaAPI 请求创建房间 " + roomId + "...", NamedTextColor.YELLOW));

		// 调用您 API 中的异步创建方法
		slimeArenaManager.createArenaAsync(templateName, roomId)
				.thenAccept(world -> {
					world.setGameRule(GameRules.ADVANCE_TIME, false);
					world.setGameRule(GameRules.ADVANCE_WEATHER, false);
					world.setGameRule(GameRules.ENTITY_DROPS, false);
					world.setGameRule(GameRules.KEEP_INVENTORY, true);
					world.setGameRule(GameRules.LOCATOR_BAR, false);
					world.setGameRule(GameRules.MOB_DROPS, false);
					world.setGameRule(GameRules.MOB_GRIEFING, false);
					world.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
					world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
					world.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
					world.setGameRule(GameRules.SPAWN_MOBS, false);
					world.setGameRule(GameRules.SPAWN_MONSTERS, false);
					world.setGameRule(GameRules.SPAWN_PATROLS, false);
					world.setGameRule(GameRules.SPAWN_PHANTOMS, false);
					world.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
					world.setGameRule(GameRules.SPAWN_WARDENS, false);
					world.setTime(13000);

					// 创建成功后，必须回到主线程绑定和操作
					Bukkit.getScheduler().runTask(plugin, () -> {
						arena.setWorld(world);
						plugin.getComponentLogger().info(Component.text("房间 " + roomId + " 地图已生成并绑定！", NamedTextColor.GREEN));
					});
				})
				.exceptionally(ex -> {
					// 如果创建失败（比如模板不存在），清理记录并报错
					plugin.getComponentLogger().error(Component.text("创建地图失败: " + ex.getMessage(), NamedTextColor.RED));
					activeRooms.remove(roomId);
					return null;
				});
	}

	/**
	 * 销毁房间并卸载地图
	 */
	public void destroyRoom(String roomId) {
		Arena arena = activeRooms.remove(roomId);
		if (arena == null) return;

		World world = arena.getWorld();
		if (world != null) {
			// 获取一个安全的回退坐标（例如主城重生点）
			Location fallbackLocation = Bukkit.getWorlds().getFirst().getSpawnLocation();

			// 调用您 API 中的安全销毁方法，自动处理残留玩家和踢出
			slimeArenaManager.discardArenaAsync(world, fallbackLocation)
					.thenRun(() -> {
						plugin.getComponentLogger().info(Component.text("房间 " + roomId + " 已通过 SlimeArenaAPI 彻底销毁。", NamedTextColor.GRAY));
					});
		}
	}

	public Arena getRoom(String roomId) {
		return activeRooms.get(roomId);
	}

	public Set<String> getActiveRoomIds() {
		return activeRooms.keySet();
	}

	/**
	 * 获取玩家当前所在的房间
	 *
	 * @return 如果玩家不在任何房间，返回 null
	 */
	public Arena getPlayerArena(Player player) {
		for (Arena arena : activeRooms.values()) {
			if (arena.hasPlayer(player)) {
				return arena;
			}
		}
		return null;
	}
}