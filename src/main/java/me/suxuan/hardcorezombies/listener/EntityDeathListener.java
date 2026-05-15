package me.suxuan.hardcorezombies.listener;

import me.suxuan.hardcorezombies.core.Arena;
import me.suxuan.hardcorezombies.core.GamePlayer;
import me.suxuan.hardcorezombies.core.GameRoomManager;
import me.suxuan.hardcorezombies.utils.PDCHelper;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public class EntityDeathListener implements Listener {

	private final GameRoomManager roomManager;

	public EntityDeathListener(GameRoomManager roomManager) {
		this.roomManager = roomManager;
	}

	@EventHandler
	public void onZombieDeath(EntityDeathEvent event) {
		LivingEntity entity = event.getEntity();

		String arenaId = PDCHelper.getString(entity, PDCHelper.ARENA_ID_KEY);
		if (arenaId == null) return;

		Arena arena = roomManager.getRoom(arenaId);
		if (arena != null) {

			event.getDrops().clear();
			event.setDroppedExp(0);

			arena.getWaveManager().onZombieDeath(entity.getUniqueId());

			Player killer = entity.getKiller();
			if (killer != null && arena.hasPlayer(killer)) {
				GamePlayer gp = arena.getGamePlayer(killer);
				if (gp != null) {
					gp.addKill();
				}
			}
		}
	}
}