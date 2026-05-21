package me.suxuan.hardcorezombies;

import me.suxuan.hardcorezombies.command.HZCommand;
import me.suxuan.hardcorezombies.config.PluginConfig;
import me.suxuan.hardcorezombies.core.GameRoomManager;
import me.suxuan.hardcorezombies.gameplay.DownedBodyManager;
import me.suxuan.hardcorezombies.gameplay.ReviveManager;
import me.suxuan.hardcorezombies.listener.ArenaProtectionListener;
import me.suxuan.hardcorezombies.listener.EntityDeathListener;
import me.suxuan.hardcorezombies.listener.PlayerGameplayListener;
import me.suxuan.hardcorezombies.weapon.WeaponListener;
import me.suxuan.slimearena.api.ArenaManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class HardcoreZombies extends JavaPlugin {

	private static HardcoreZombies instance;

	private PluginConfig pluginConfig;
	private GameRoomManager roomManager;
	private DownedBodyManager downedBodyManager;
	private ReviveManager reviveManager;

	@Override
	public void onEnable() {
		instance = this;
		this.pluginConfig = new PluginConfig(this);
		pluginConfig.load();

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
		this.downedBodyManager = new DownedBodyManager();
		this.reviveManager = new ReviveManager(this, roomManager, downedBodyManager);

		HZCommand commandExecutor = new HZCommand(this, roomManager);
		PluginCommand command = getCommand("hzombies");
		if (command != null) {
			command.setExecutor(commandExecutor);
			command.setTabCompleter(commandExecutor);
		}

		getServer().getPluginManager().registerEvents(new WeaponListener(roomManager), this);
		getServer().getPluginManager().registerEvents(new EntityDeathListener(roomManager), this);
		getServer().getPluginManager().registerEvents(
				new PlayerGameplayListener(roomManager, reviveManager, downedBodyManager),
				this
		);
		getServer().getPluginManager().registerEvents(new ArenaProtectionListener(roomManager), this);

		getComponentLogger().info(Component.text("HardcoreZombies 插件已启动！", NamedTextColor.GREEN));
	}

	@Override
	public void onDisable() {
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

	public PluginConfig getPluginConfig() {
		return pluginConfig;
	}

	public GameRoomManager getRoomManager() {
		return roomManager;
	}

	public DownedBodyManager getDownedBodyManager() {
		return downedBodyManager;
	}
}
