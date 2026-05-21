package me.suxuan.hardcorezombies.util;

import me.suxuan.hardcorezombies.HardcoreZombies;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

public final class PlayerResetUtil {

	private PlayerResetUtil() {
	}

	public static void resetToLobby(Player player, Location lobby) {
		HardcoreZombies plugin = HardcoreZombies.getInstance();
		if (plugin != null && plugin.getRoomManager() != null) {
			plugin.getRoomManager().getCollisionManager().refreshPlayer(player);
		}

		player.getInventory().clear();
		for (PotionEffect effect : player.getActivePotionEffects()) {
			player.removePotionEffect(effect.getType());
		}
		player.setGameMode(GameMode.SURVIVAL);
		player.setFireTicks(0);
		player.setLevel(0);
		player.setExp(0f);
		var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
		if (maxHealth != null) {
			player.setHealth(maxHealth.getValue());
		}
		player.setFoodLevel(20);
		player.setSaturation(20f);
		player.teleportAsync(lobby);
	}
}
