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
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class HZCommand implements CommandExecutor, TabCompleter {

	private static final String PERM_ADMIN = "hardcorezombies.admin";
	private static final String PERM_PLAY = "hardcorezombies.play";

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
			sendUsage(player);
			return true;
		}

		String subCommand = args[0].toLowerCase();

		switch (subCommand) {
			case "create" -> handleCreate(player, args);
			case "join" -> handleJoin(player, args);
			case "leave" -> handleLeave(player);
			case "start" -> handleStart(player);
			case "delete" -> handleDelete(player, args);
			case "list" -> handleList(player);
			case "addsnode" -> handleAddSpawnNode(player);
			default -> player.sendMessage(Component.text("未知子指令！", NamedTextColor.RED));
		}
		return true;
	}

	private void handleCreate(Player player, String[] args) {
		if (!player.hasPermission(PERM_ADMIN)) {
			player.sendMessage(Component.text("你没有权限执行此操作。", NamedTextColor.RED));
			return;
		}

		String templateName = args.length >= 2 ? args[1] : "void";
		player.sendMessage(Component.text("正在请求创建房间，模板: " + templateName, NamedTextColor.YELLOW));

		gameRoomManager.createRoom(templateName, roomId -> player.sendMessage(Component.text(
				"房间已就绪！ID: " + roomId + " — 使用 /hz join " + roomId,
				NamedTextColor.GREEN
		)));
	}

	private void handleJoin(Player player, String[] args) {
		if (!player.hasPermission(PERM_PLAY)) {
			player.sendMessage(Component.text("你没有权限执行此操作。", NamedTextColor.RED));
			return;
		}
		if (args.length < 2) {
			player.sendMessage(Component.text("用法: /hz join <房间ID>", NamedTextColor.RED));
			return;
		}

		Arena arena = gameRoomManager.getRoom(args[1]);
		if (arena == null) {
			player.sendMessage(Component.text("找不到该房间或房间仍在加载中！", NamedTextColor.RED));
			return;
		}
		arena.addPlayer(player);
	}

	private void handleLeave(Player player) {
		if (!player.hasPermission(PERM_PLAY)) {
			player.sendMessage(Component.text("你没有权限执行此操作。", NamedTextColor.RED));
			return;
		}

		Arena arena = gameRoomManager.getPlayerArena(player);
		if (arena != null) {
			arena.removePlayer(player);
			player.sendMessage(Component.text("你已离开房间。", NamedTextColor.GREEN));
		} else {
			player.sendMessage(Component.text("你当前不在任何房间内。", NamedTextColor.RED));
		}
	}

	private void handleStart(Player player) {
		if (!player.hasPermission(PERM_ADMIN)) {
			player.sendMessage(Component.text("你没有权限执行此操作。", NamedTextColor.RED));
			return;
		}

		Arena arena = gameRoomManager.getPlayerArena(player);
		if (arena != null && (arena.getState() == GameState.WAITING || arena.getState() == GameState.STARTING)) {
			arena.forceStart();
			arena.broadcast(Component.text("游戏已被管理员强制开始！", NamedTextColor.RED));
		} else {
			player.sendMessage(Component.text("你不在一个等待中的房间内。", NamedTextColor.RED));
		}
	}

	private void handleDelete(Player player, String[] args) {
		if (!player.hasPermission(PERM_ADMIN)) {
			player.sendMessage(Component.text("你没有权限执行此操作。", NamedTextColor.RED));
			return;
		}
		if (args.length < 2) {
			player.sendMessage(Component.text("用法: /hz delete <房间ID>", NamedTextColor.RED));
			return;
		}

		Arena arena = gameRoomManager.getRoom(args[1]);
		if (arena == null) {
			player.sendMessage(Component.text("找不到该房间！", NamedTextColor.RED));
			return;
		}
		gameRoomManager.destroyRoom(args[1]);
		player.sendMessage(Component.text("房间 " + args[1] + " 已销毁。", NamedTextColor.GREEN));
	}

	private void handleList(Player player) {
		if (!player.hasPermission(PERM_PLAY)) {
			player.sendMessage(Component.text("你没有权限执行此操作。", NamedTextColor.RED));
			return;
		}

		if (gameRoomManager.getActiveRoomIds().isEmpty()) {
			player.sendMessage(Component.text("当前没有活跃房间。", NamedTextColor.GRAY));
			return;
		}

		player.sendMessage(Component.text("— 活跃房间 —", NamedTextColor.GOLD));
		for (String id : gameRoomManager.getActiveRoomIds()) {
			Arena arena = gameRoomManager.getRoom(id);
			if (arena == null) continue;
			String mapStatus = arena.getWorld() != null ? "已加载" : "加载中";
			player.sendMessage(Component.text(
					id + " | 模板: " + arena.getTemplateName()
							+ " | " + arena.getState()
							+ " | 玩家: " + arena.getGamePlayers().size()
							+ " | 地图: " + mapStatus,
					NamedTextColor.GRAY
			));
		}
	}

	private void handleAddSpawnNode(Player player) {
		if (!player.hasPermission(PERM_ADMIN)) {
			player.sendMessage(Component.text("你没有权限执行此操作。", NamedTextColor.RED));
			return;
		}

		Arena arena = gameRoomManager.getPlayerArena(player);
		if (arena == null || arena.getWorld() == null) {
			player.sendMessage(Component.text("你必须在一个已加载地图的房间内。", NamedTextColor.RED));
			return;
		}

		Vector offset = player.getLocation().toVector()
				.subtract(arena.getWorld().getSpawnLocation().toVector());
		plugin.getPluginConfig().addSpawnOffset(arena.getTemplateName(), offset);
		player.sendMessage(Component.text(
				"已为模板 \"" + arena.getTemplateName() + "\" 添加刷怪点偏移: "
						+ offset.getBlockX() + "," + offset.getBlockY() + "," + offset.getBlockZ(),
				NamedTextColor.GREEN
		));
	}

	private void sendUsage(Player player) {
		player.sendMessage(Component.text("用法: /hz <子指令>", NamedTextColor.YELLOW));
		player.sendMessage(Component.text("  create [模板] | join <ID> | leave | list", NamedTextColor.GRAY));
		if (player.hasPermission(PERM_ADMIN)) {
			player.sendMessage(Component.text("  start | delete <ID> | addsnode", NamedTextColor.GRAY));
		}
	}

	@Override
	public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
		List<String> completions = new ArrayList<>();
		if (args.length == 1) {
			completions.add("join");
			completions.add("leave");
			completions.add("list");
			if (sender.hasPermission(PERM_ADMIN)) {
				completions.add("create");
				completions.add("start");
				completions.add("delete");
				completions.add("addsnode");
			}
		} else if (args.length == 2 && (args[0].equalsIgnoreCase("join") || args[0].equalsIgnoreCase("delete"))) {
			completions.addAll(gameRoomManager.getActiveRoomIds());
		}
		return completions;
	}
}
