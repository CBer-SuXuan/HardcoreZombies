package me.suxuan.hardcorezombies;

import me.suxuan.hardcorezombies.command.HZCommand;
import me.suxuan.hardcorezombies.core.GameRoomManager;
import me.suxuan.hardcorezombies.listener.EntityDeathListener;
import me.suxuan.hardcorezombies.weapon.WeaponListener;
import me.suxuan.slimearena.api.ArenaManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class HardcoreZombies extends JavaPlugin {

	private static HardcoreZombies instance;
	private GameRoomManager roomManager;

	@Override
	public void onEnable() {
		instance = this;

		RegisteredServiceProvider<ArenaManager> provider = getServer().getServicesManager().getRegistration(ArenaManager.class);
		ArenaManager slimeArenaManager;
		if (provider != null) {
			slimeArenaManager = provider.getProvider();
			getComponentLogger().info(Component.text("成功连接到 SlimeArenaAPI！", NamedTextColor.GREEN));
		} else {
			getComponentLogger().error(Component.text("未找到 SlimeArenaAPI 服务，插件将禁用！", NamedTextColor.RED));
			getServer().getPluginManager().disablePlugin(this);
			return;
		}

		this.roomManager = new GameRoomManager(this, slimeArenaManager);

		HZCommand commandExecutor = new HZCommand(this, roomManager);
		PluginCommand command = getCommand("hzombies");
		if (command != null) {
			command.setExecutor(commandExecutor);
			command.setTabCompleter(commandExecutor);
		}

		getServer().getPluginManager().registerEvents(new WeaponListener(roomManager), this);
		getServer().getPluginManager().registerEvents(new EntityDeathListener(roomManager), this);

		getComponentLogger().info(Component.text("HardcoreZombies 插件已启动！", NamedTextColor.GREEN));
	}

	@Override
	public void onDisable() {
		// 游戏卸载时，清理所有房间
		if (roomManager != null) {
			for (String arenaId : roomManager.getActiveRoomIds()) {
				roomManager.destroyRoom(arenaId);
			}
		}
		getComponentLogger().info(Component.text("HardcoreZombies 插件已卸载。", NamedTextColor.YELLOW));
	}

	public static HardcoreZombies getInstance() {
		return instance;
	}
}