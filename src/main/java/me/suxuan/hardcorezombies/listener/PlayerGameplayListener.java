package me.suxuan.hardcorezombies.listener;

import me.suxuan.hardcorezombies.HardcoreZombies;
import me.suxuan.hardcorezombies.core.Arena;
import me.suxuan.hardcorezombies.core.GamePlayer;
import me.suxuan.hardcorezombies.core.GameRoomManager;
import me.suxuan.hardcorezombies.core.GameState;
import me.suxuan.hardcorezombies.gameplay.DownedBodyManager;
import me.suxuan.hardcorezombies.gameplay.ReviveManager;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class PlayerGameplayListener implements Listener {

	private final GameRoomManager roomManager;
	private final ReviveManager reviveManager;
	private final DownedBodyManager downedBodyManager;

	public PlayerGameplayListener(
			GameRoomManager roomManager,
			ReviveManager reviveManager,
			DownedBodyManager downedBodyManager
	) {
		this.roomManager = roomManager;
		this.reviveManager = reviveManager;
		this.downedBodyManager = downedBodyManager;
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onFriendlyFire(EntityDamageByEntityEvent event) {
		if (!(event.getEntity() instanceof Player victim)) return;

		Player attacker = resolvePlayerAttacker(event.getDamager());
		if (attacker == null) return;

		Arena victimArena = roomManager.getPlayerArena(victim);
		if (victimArena == null || victimArena.getState() != GameState.IN_GAME) return;

		if (victimArena.hasPlayer(attacker)) {
			event.setCancelled(true);
		}
	}

	/** 倒地玩家不能造成任何伤害 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onDownedAttack(EntityDamageByEntityEvent event) {
		Player attacker = resolvePlayerAttacker(event.getDamager());
		if (attacker == null) return;

		Arena arena = roomManager.getPlayerArena(attacker);
		if (arena == null || arena.getState() != GameState.IN_GAME) return;

		GamePlayer gp = arena.getGamePlayer(attacker);
		if (gp != null && gp.isDowned()) {
			event.setCancelled(true);
		}
	}

	/** 倒地假身（Mannequin）不受伤害 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onDownedBodyDamage(EntityDamageEvent event) {
		if (!(event.getEntity() instanceof LivingEntity living)) return;
		if (downedBodyManager.isDownedBodyEntity(living)) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onPlayerDamage(EntityDamageEvent event) {
		if (!(event.getEntity() instanceof Player player)) return;

		Arena arena = roomManager.getPlayerArena(player);
		if (arena == null || arena.getState() != GameState.IN_GAME) return;

		GamePlayer gp = arena.getGamePlayer(player);
		if (gp == null || gp.isDead()) {
			event.setCancelled(true);
			return;
		}

		if (gp.isDowned()) {
			event.setCancelled(true);
			return;
		}

		double remaining = player.getHealth() - event.getFinalDamage();
		if (remaining <= 0) {
			event.setCancelled(true);
			gp.enterDownedState();
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onRegainHealth(EntityRegainHealthEvent event) {
		if (!(event.getEntity() instanceof Player player)) return;

		GamePlayer gp = getDownedGamePlayer(player);
		if (gp != null) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerDeath(PlayerDeathEvent event) {
		Player player = event.getEntity();
		Arena arena = roomManager.getPlayerArena(player);
		if (arena == null || arena.getState() != GameState.IN_GAME) return;

		GamePlayer gp = arena.getGamePlayer(player);
		if (gp == null) return;

		event.setKeepInventory(true);
		event.setKeepLevel(true);
		event.getDrops().clear();
		event.setDroppedExp(0);
		event.deathMessage(null);

		Location deathLoc = player.getLocation().clone();

		HardcoreZombies plugin = HardcoreZombies.getInstance();
		plugin.getServer().getScheduler().runTask(plugin, () -> {
			if (!player.isOnline()) return;

			if (player.isDead()) {
				player.spigot().respawn();
			}
			player.teleportAsync(deathLoc);

			if (!gp.isDowned() && !gp.isDead()) {
				gp.enterDownedState();
			} else if (gp.isDowned()) {
				gp.setDead(true);
			}
		});
	}

	/**
	 * 倒地时 Attribute 已禁止移动；此处仅在玩家发生位移时把坐标拉回假身头部（不调用 teleport）。
	 * 仅转头时不修改 to，避免干扰视角。
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onDownedMove(PlayerMoveEvent event) {
		Player player = event.getPlayer();
		GamePlayer gp = getDownedGamePlayer(player);
		if (gp == null) return;

		Location from = event.getFrom();
		Location to = event.getTo();
		if (to == null) return;

		if (!hasMovedPosition(from, to)) {
			return;
		}

		Location anchor = downedBodyManager.getCameraAnchor(gp, to.getYaw(), to.getPitch());
		if (anchor == null) return;

		event.setTo(anchor);
	}

	private static boolean hasMovedPosition(Location from, Location to) {
		return from.getBlockX() != to.getBlockX()
				|| from.getBlockY() != to.getBlockY()
				|| from.getBlockZ() != to.getBlockZ()
				|| (Math.abs(from.getX() - to.getX()) > 1.0E-4)
				|| (Math.abs(from.getY() - to.getY()) > 1.0E-4)
				|| (Math.abs(from.getZ() - to.getZ()) > 1.0E-4);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onDownedInteract(PlayerInteractEvent event) {
		if (getDownedGamePlayer(event.getPlayer()) != null) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onDownedSwapHand(PlayerSwapHandItemsEvent event) {
		if (getDownedGamePlayer(event.getPlayer()) != null) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onDownedHeldSlot(PlayerItemHeldEvent event) {
		if (getDownedGamePlayer(event.getPlayer()) != null) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onDownedDrop(PlayerDropItemEvent event) {
		if (getDownedGamePlayer(event.getPlayer()) != null) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onDownedPickup(EntityPickupItemEvent event) {
		if (event.getEntity() instanceof Player player && getDownedGamePlayer(player) != null) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onStopSneak(PlayerToggleSneakEvent event) {
		if (event.isSneaking()) return;
		reviveManager.cancelRevive(event.getPlayer().getUniqueId());
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		reviveManager.cancelRevive(player.getUniqueId());

		Arena arena = roomManager.getPlayerArena(player);
		if (arena != null) {
			arena.removePlayer(player, true);
		}
	}

	private GamePlayer getDownedGamePlayer(Player player) {
		Arena arena = roomManager.getPlayerArena(player);
		if (arena == null || arena.getState() != GameState.IN_GAME) return null;
		GamePlayer gp = arena.getGamePlayer(player);
		if (gp != null && gp.isDowned()) return gp;
		return null;
	}

	private static Player resolvePlayerAttacker(Entity damager) {
		if (damager instanceof Player player) {
			return player;
		}
		if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
			return shooter;
		}
		return null;
	}
}
