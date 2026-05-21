package me.suxuan.hardcorezombies.listener;

import me.suxuan.hardcorezombies.command.HZCommand;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class NpcJoinListener implements Listener {

	private final HZCommand commandExecutor;

	public NpcJoinListener(HZCommand commandExecutor) {
		this.commandExecutor = commandExecutor;
	}

	@EventHandler
	public void onNpcRightClick(NPCRightClickEvent event) {
		Player player = event.getClicker();
		commandExecutor.markNpcJoin(player);
	}
}
