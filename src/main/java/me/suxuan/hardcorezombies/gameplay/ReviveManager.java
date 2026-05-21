package me.suxuan.hardcorezombies.gameplay;

import me.suxuan.hardcorezombies.HardcoreZombies;
import me.suxuan.hardcorezombies.config.PluginConfig;
import me.suxuan.hardcorezombies.core.Arena;
import me.suxuan.hardcorezombies.core.GamePlayer;
import me.suxuan.hardcorezombies.core.GameRoomManager;
import me.suxuan.hardcorezombies.core.GameState;
import org.bukkit.Location;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 救援规则：救援者必须下蹲，并停留在倒地队友附近，持续读条后完成救援。
 */
public class ReviveManager {

	private final GameRoomManager roomManager;
	private final DownedBodyManager downedBodyManager;
	private final Map<UUID, ReviveSession> activeSessions = new ConcurrentHashMap<>();

	public ReviveManager(HardcoreZombies plugin, GameRoomManager roomManager, DownedBodyManager downedBodyManager) {
		this.roomManager = roomManager;
		this.downedBodyManager = downedBodyManager;
		startProximityScanner(plugin);
	}

	private void startProximityScanner(HardcoreZombies plugin) {
		new BukkitRunnable() {
			@Override
			public void run() {
				scanSneakingRevivers();
			}
		}.runTaskTimer(plugin, 0L, 4L);
	}

	private void scanSneakingRevivers() {
		PluginConfig config = HardcoreZombies.getInstance().getPluginConfig();
		double rangeSq = config.getReviveRangeSquared();

		for (Arena arena : roomManager.getActiveRoomIds().stream()
				.map(roomManager::getRoom)
				.filter(a -> a != null && a.getState() == GameState.IN_GAME)
				.toList()) {

			for (GamePlayer reviverGp : arena.getGamePlayers()) {
				if (!reviverGp.isActiveCombatant()) {
					cancelRevive(reviverGp.getUuid());
					continue;
				}

				Player reviver = reviverGp.getPlayer();
				if (reviver == null || !reviver.isOnline()) continue;

				if (!reviver.isSneaking()) {
					cancelRevive(reviver.getUniqueId());
					continue;
				}

				Player nearestDowned = findNearestDownedTeammate(reviver, arena, rangeSq);
				if (nearestDowned == null) {
					cancelRevive(reviver.getUniqueId());
					continue;
				}

				ensureRevive(reviver, nearestDowned, arena);
			}
		}
	}

	private Player findNearestDownedTeammate(Player reviver, Arena arena, double rangeSq) {
		Player nearest = null;
		double nearestDist = rangeSq;

		for (GamePlayer teammate : arena.getGamePlayers()) {
			if (!teammate.isDowned() || teammate.isDead()) continue;

			Player target = teammate.getPlayer();
			if (target == null || !target.isOnline() || target.equals(reviver)) continue;
			if (!reviver.getWorld().equals(target.getWorld())) continue;

			Location targetLoc = downedBodyManager.getBodyLocation(teammate);
			if (targetLoc == null) {
				targetLoc = target.getLocation();
			}
			double dist = reviver.getLocation().distanceSquared(targetLoc);
			if (dist <= nearestDist) {
				nearestDist = dist;
				nearest = target;
			}
		}
		return nearest;
	}

	public void ensureRevive(Player reviver, Player target, Arena arena) {
		ReviveSession existing = activeSessions.get(reviver.getUniqueId());
		if (existing != null && existing.targetId().equals(target.getUniqueId())) {
			return;
		}
		startRevive(reviver, target, arena);
	}

	public void startRevive(Player reviver, Player target, Arena arena) {
		cancelRevive(reviver.getUniqueId());

		PluginConfig config = HardcoreZombies.getInstance().getPluginConfig();
		int totalTicks = config.getReviveChannelTicks();
		double rangeSq = config.getReviveRangeSquared();

		ReviveSession session = new ReviveSession(reviver.getUniqueId(), target.getUniqueId(), arena.getArenaId());
		BukkitTask task = new BukkitRunnable() {
			int ticks = 0;

			@Override
			public void run() {
				if (!reviver.isOnline() || !target.isOnline()) {
					cancelRevive(reviver.getUniqueId());
					return;
				}

				if (!reviver.isSneaking()) {
					reviver.sendActionBar(Component.text("松开下蹲，救援中断", NamedTextColor.RED));
					cancelRevive(reviver.getUniqueId());
					return;
				}

				Arena currentArena = roomManager.getRoom(session.arenaId());
				if (currentArena == null || currentArena.getState() != GameState.IN_GAME) {
					cancelRevive(reviver.getUniqueId());
					return;
				}

				GamePlayer downed = currentArena.getGamePlayer(target);
				GamePlayer reviverGp = currentArena.getGamePlayer(reviver);
				if (downed == null || reviverGp == null || !downed.isDowned() || downed.isDead()
						|| !reviverGp.isActiveCombatant()) {
					cancelRevive(reviver.getUniqueId());
					return;
				}

				Location bodyLoc = downedBodyManager.getBodyLocation(downed);
				if (bodyLoc == null) {
					bodyLoc = target.getLocation();
				}
				if (!reviver.getWorld().equals(target.getWorld())
						|| reviver.getLocation().distanceSquared(bodyLoc) > rangeSq) {
					reviver.sendActionBar(Component.text("离队友太远，救援中断！", NamedTextColor.RED));
					cancelRevive(reviver.getUniqueId());
					return;
				}

				ticks++;
				float progress = (float) ticks / totalTicks;
				reviver.sendActionBar(Component.text(
						"正在救援 " + target.getName() + " (" + (totalTicks - ticks) / 20 + "s)",
						NamedTextColor.GREEN
				));
				reviver.setExp(Math.min(0.99f, progress));

				if (ticks >= totalTicks) {
					downed.revive();
					reviver.sendMessage(Component.text("你成功救起了 " + target.getName() + "！", NamedTextColor.GREEN));
					currentArena.broadcast(Component.text(
							reviver.getName() + " 救起了 " + target.getName() + "！",
							NamedTextColor.GREEN
					));
					cancelRevive(reviver.getUniqueId());
				}
			}
		}.runTaskTimer(HardcoreZombies.getInstance(), 0L, 1L);

		activeSessions.put(reviver.getUniqueId(), session.setTask(task));
	}

	public void cancelRevive(UUID reviverId) {
		ReviveSession session = activeSessions.remove(reviverId);
		if (session == null) return;

		if (session.task() != null) {
			session.task().cancel();
		}

		Player reviver = HardcoreZombies.getInstance().getServer().getPlayer(reviverId);
		if (reviver != null) {
			reviver.setExp(0f);
		}
	}

	public boolean isReviving(UUID reviverId) {
		return activeSessions.containsKey(reviverId);
	}

	private record ReviveSession(UUID reviverId, UUID targetId, String arenaId, BukkitTask task) {
		ReviveSession(UUID reviverId, UUID targetId, String arenaId) {
			this(reviverId, targetId, arenaId, null);
		}

		ReviveSession setTask(BukkitTask task) {
			return new ReviveSession(reviverId, targetId, arenaId, task);
		}
	}
}
