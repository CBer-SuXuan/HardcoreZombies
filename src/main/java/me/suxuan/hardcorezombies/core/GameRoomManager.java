package me.suxuan.hardcorezombies.core;

import me.suxuan.hardcorezombies.HardcoreZombies;
import me.suxuan.hardcorezombies.gameplay.PlayerCollisionManager;
import me.suxuan.hardcorezombies.utils.PDCHelper;
import me.suxuan.slimearena.api.ArenaManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class GameRoomManager {

	private final HardcoreZombies plugin;
	private final ArenaManager slimeArenaManager;

	private final Map<String, Arena> activeRooms = new ConcurrentHashMap<>();
	private final PlayerCollisionManager collisionManager;

	public GameRoomManager(HardcoreZombies plugin, ArenaManager slimeArenaManager) {
		this.plugin = plugin;
		this.slimeArenaManager = slimeArenaManager;
		this.collisionManager = new PlayerCollisionManager(this);
	}

	public PlayerCollisionManager getCollisionManager() {
		return collisionManager;
	}

	public String createRoom(String templateName, Consumer<String> onCreated) {
		String roomId = "hz_arena_" + UUID.randomUUID().toString().substring(0, 8);
		Arena arena = new Arena(roomId, templateName, this);
		activeRooms.put(roomId, arena);

		plugin.getComponentLogger().info(Component.text(
				"正在向 SlimeArenaAPI 请求创建房间 " + roomId + "...",
				NamedTextColor.YELLOW
		));

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

					Bukkit.getScheduler().runTask(plugin, () -> {
						Arena loaded = activeRooms.get(roomId);
						if (loaded == null) return;

						loaded.setWorld(world);
						plugin.getComponentLogger().info(Component.text(
								"房间 " + roomId + " 地图已生成并绑定！",
								NamedTextColor.GREEN
						));
						loaded.tryAutoStart();
						if (onCreated != null) {
							onCreated.accept(roomId);
						}
					});
				})
				.exceptionally(ex -> {
					plugin.getComponentLogger().error(Component.text(
							"创建地图失败: " + ex.getMessage(),
							NamedTextColor.RED
					));
					Arena failed = activeRooms.remove(roomId);
					if (failed != null) {
						Bukkit.getScheduler().runTask(plugin, failed::shutdown);
					}
					return null;
				});

		return roomId;
	}

	public void destroyRoom(String roomId) {
		Arena arena = activeRooms.remove(roomId);
		if (arena == null) return;

		arena.shutdown();

		World world = arena.getWorld();
		if (world != null) {
			Location fallbackLocation = plugin.getPluginConfig().getLobbyLocation();
			slimeArenaManager.discardArenaAsync(world, fallbackLocation)
					.thenRun(() -> plugin.getComponentLogger().info(Component.text(
							"房间 " + roomId + " 已通过 SlimeArenaAPI 彻底销毁。",
							NamedTextColor.GRAY
					)));
		}
	}

	public Arena getRoom(String roomId) {
		return activeRooms.get(roomId);
	}

	public Set<String> getActiveRoomIds() {
		return activeRooms.keySet();
	}

	public Arena getPlayerArena(Player player) {
		for (Arena arena : activeRooms.values()) {
			if (arena.hasPlayer(player)) {
				return arena;
			}
		}
		return null;
	}

	public boolean isInGameWorld(Player player) {
		Arena arena = getPlayerArena(player);
		if (arena == null) return false;
		World world = arena.getWorld();
		return world != null && player.getWorld().equals(world);
	}

	/**
	 * 同房间玩家或队友的倒地假身，不算有效攻击目标（不造成伤害、不奖励金币）。
	 */
	public boolean isFriendlyCombatTarget(Player shooter, Entity target) {
		Arena shooterArena = getPlayerArena(shooter);
		if (shooterArena == null || shooterArena.getState() != GameState.IN_GAME) {
			return false;
		}

		if (target instanceof Player targetPlayer) {
			return shooterArena.hasPlayer(targetPlayer);
		}

		if (target instanceof LivingEntity living) {
			String ownerId = PDCHelper.getString(living, PDCHelper.DOWNED_BODY_KEY);
			if (ownerId != null) {
				try {
					UUID ownerUuid = UUID.fromString(ownerId);
					return shooterArena.getGamePlayers().stream()
							.anyMatch(gp -> gp.getUuid().equals(ownerUuid));
				} catch (IllegalArgumentException ignored) {
					// ignore malformed uuid
				}
			}
		}

		return false;
	}
}
