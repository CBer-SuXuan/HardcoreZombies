package me.suxuan.hardcorezombies.command;

import me.suxuan.hardcorezombies.HardcoreZombies;
import me.suxuan.hardcorezombies.core.Arena;
import me.suxuan.hardcorezombies.core.GameRoomManager;
import me.suxuan.hardcorezombies.core.GameState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class HZCommand implements CommandExecutor, TabCompleter {

	private final HardcoreZombies plugin;
	private final GameRoomManager gameRoomManager;

	public HZCommand(HardcoreZombies plugin, GameRoomManager gameRoomManager) {
		this.plugin = plugin;
		this.gameRoomManager = gameRoomManager;
	}

	@Override
	public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(Component.text("只有玩家可以执行此指令！", NamedTextColor.RED));
			return true;
		}

		if (args.length == 0) {
			player.sendMessage(Component.text("用法: /hz <create|join|leave|start>", NamedTextColor.YELLOW));
			return true;
		}

		String subCommand = args[0].toLowerCase();

		switch (subCommand) {
			case "create":
				if (args.length == 1) {
					gameRoomManager.createRoom("void");
					break;
				}
				if (args.length < 2) {
					player.sendMessage(Component.text("用法: /hz create <ASWM模板名称>", NamedTextColor.RED));
					return true;
				}
				String templateName = args[1];
				player.sendMessage(Component.text("正在请求创建房间，模板: " + templateName, NamedTextColor.YELLOW));
				// 异步克隆和加载世界
				gameRoomManager.createRoom(templateName);
				break;

			case "join":
				if (args.length < 2) {
					player.sendMessage(Component.text("用法: /hz join <房间ID>", NamedTextColor.RED));
					return true;
				}
				String roomId = args[1];
				Arena arena = gameRoomManager.getRoom(roomId);
				if (arena == null) {
					player.sendMessage(Component.text("找不到该房间或房间仍在加载中！", NamedTextColor.RED));
					return true;
				}
				arena.addPlayer(player);
				break;

			case "start":
				// 仅作测试用途，强制推进房间状态
				// 实际逻辑中，应该检测是否所有玩家已准备好，或者倒计时结束
				Arena currentArena = getPlayerArena(player);
				if (currentArena != null && currentArena.getState() == GameState.WAITING) {
					currentArena.startGame();
					currentArena.broadcast(Component.text("游戏已被管理员强制开始！", NamedTextColor.RED));
				} else {
					player.sendMessage(Component.text("你不在一个等待中的房间内。", NamedTextColor.RED));
				}
				break;

			case "leave":
				Arena leavingArena = getPlayerArena(player);
				if (leavingArena != null) {
					leavingArena.removePlayer(player);
					player.sendMessage(Component.text("你已离开房间。", NamedTextColor.GREEN));
				} else {
					player.sendMessage(Component.text("你当前不在任何房间内。", NamedTextColor.RED));
				}
				break;

			case "delete":
				if (args.length < 2) {
					player.sendMessage(Component.text("用法: /hz delete <房间ID>", NamedTextColor.RED));
					return true;
				}
				String roomIdToDelete = args[1];
				Arena arenaToDelete = gameRoomManager.getRoom(roomIdToDelete);
				if (arenaToDelete == null) {
					player.sendMessage(Component.text("找不到该房间或房间仍在加载中！", NamedTextColor.RED));
					return true;
				}
				gameRoomManager.destroyRoom(roomIdToDelete);
				break;


			default:
				player.sendMessage(Component.text("未知子指令！", NamedTextColor.RED));
				break;
		}
		return true;
	}

	@Override
	public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
		List<String> completions = new ArrayList<>();
		if (args.length == 1) {
			completions.add("create");
			completions.add("join");
			completions.add("leave");
			completions.add("start");
			completions.add("delete");
		} else if (args.length == 2 && (args[0].equalsIgnoreCase("join") || args[0].equalsIgnoreCase("delete"))) {
			// 自动补全当前所有活跃的房间ID
			completions.addAll(gameRoomManager.getActiveRoomIds());
		}
		return completions;
	}

	/**
	 * 辅助方法：查找玩家当前所在的房间
	 */
	private Arena getPlayerArena(Player player) {
		for (String id : gameRoomManager.getActiveRoomIds()) {
			Arena a = gameRoomManager.getRoom(id);
			if (a != null && a.hasPlayer(player)) {
				return a;
			}
		}
		return null;
	}
}