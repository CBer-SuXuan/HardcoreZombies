package me.suxuan.hardcorezombies.gameplay;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import me.suxuan.hardcorezombies.HardcoreZombies;
import me.suxuan.hardcorezombies.core.Arena;
import me.suxuan.hardcorezombies.core.GamePlayer;
import me.suxuan.hardcorezombies.core.GameState;
import me.suxuan.hardcorezombies.utils.PDCHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 倒地假身：Mannequin 平躺模型；真身通过 Attribute 禁止移动，视角在假身头部环顾。
 */
public class DownedBodyManager {

	private final Map<UUID, DownedSession> sessions = new ConcurrentHashMap<>();

	public void spawn(GamePlayer gamePlayer) {
		remove(gamePlayer);

		Player player = gamePlayer.getPlayer();
		Location bodyLoc = player.getLocation().clone();
		bodyLoc.setY(Math.floor(bodyLoc.getY()) + 0.125);

		Mannequin body = bodyLoc.getWorld().spawn(bodyLoc, Mannequin.class, mannequin -> {
			mannequin.setProfile(ResolvableProfile.resolvableProfile(player.getPlayerProfile()));
			mannequin.setImmovable(true);
			mannequin.setInvulnerable(true);
			mannequin.setAI(false);
			mannequin.setCollidable(false);
			mannequin.setGravity(false);
			mannequin.setSilent(true);
			mannequin.setPersistent(false);
			mannequin.setRemoveWhenFarAway(false);
			mannequin.setCanPickupItems(false);

			mannequin.customName(Component.text(player.getName() + " (倒地)", NamedTextColor.DARK_RED));
			mannequin.setCustomNameVisible(true);
			mannequin.setDescription(Component.text("等待救援", NamedTextColor.GRAY));

			applyLyingPose(mannequin, bodyLoc.getYaw());
			copyEquipment(player, mannequin);

			PDCHelper.setString(mannequin, PDCHelper.DOWNED_BODY_KEY, player.getUniqueId().toString());
		});

		DownedAttributeLock savedAttributes = DownedAttributeLock.apply(player);

		player.setInvisible(true);
		player.setGliding(false);
		player.setSwimming(false);

		// 仅初次就位到假身头部（不是循环传送锁位）
		Location initialView = computeCameraPosition(body);
		initialView.setYaw(player.getLocation().getYaw());
		initialView.setPitch(player.getLocation().getPitch());
		player.teleportAsync(initialView);

		sessions.put(player.getUniqueId(), new DownedSession(
				body.getUniqueId(),
				bodyLoc.clone(),
				computeCameraPosition(body),
				savedAttributes
		));

		HardcoreZombies.getInstance().getRoomManager().getCollisionManager()
				.onPlayerDowned(player, gamePlayer.getArena());
		HardcoreZombies.getInstance().getWeaponListener().clearPlayerWeaponState(player);
	}

	public void remove(GamePlayer gamePlayer) {
		DownedSession session = sessions.remove(gamePlayer.getUuid());
		if (session == null) return;

		Player player = gamePlayer.getPlayer();
		if (player != null && player.isOnline()) {
			session.savedAttributes().restore(player);
			player.setInvisible(false);
			player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
			Arena arena = gamePlayer.getArena();
			if (arena.getState() == GameState.IN_GAME) {
				HardcoreZombies.getInstance().getRoomManager().getCollisionManager().applyArena(arena);
			} else {
				HardcoreZombies.getInstance().getRoomManager().getCollisionManager().refreshPlayer(player);
			}
		}

		LivingEntity body = getBody(session.bodyUuid());
		if (body != null) {
			body.remove();
		}
	}

	@Nullable
	public Location getBodyLocation(GamePlayer gamePlayer) {
		DownedSession session = sessions.get(gamePlayer.getUuid());
		if (session == null) return null;
		return session.bodyLocation().clone();
	}

	@Nullable
	public Location getCameraPosition(GamePlayer gamePlayer) {
		DownedSession session = sessions.get(gamePlayer.getUuid());
		if (session == null) return null;

		LivingEntity body = getBody(session.bodyUuid());
		if (body != null) {
			return computeCameraPosition(body);
		}
		return session.cameraAnchor().clone();
	}

	@Nullable
	public Location getCameraAnchor(GamePlayer gamePlayer, float yaw, float pitch) {
		Location pos = getCameraPosition(gamePlayer);
		if (pos == null) return null;
		pos.setYaw(yaw);
		pos.setPitch(pitch);
		return pos;
	}

	public boolean hasBody(GamePlayer gamePlayer) {
		return sessions.containsKey(gamePlayer.getUuid());
	}

	public boolean isDownedBodyEntity(LivingEntity entity) {
		return PDCHelper.getString(entity, PDCHelper.DOWNED_BODY_KEY) != null;
	}

	private static void applyLyingPose(Mannequin mannequin, float yaw) {
		mannequin.setRotation(yaw, 0);
		mannequin.setPose(Pose.SLEEPING, true);
	}

	private static void copyEquipment(Player player, Mannequin mannequin) {
		PlayerInventory inv = player.getInventory();
		EntityEquipment equipment = mannequin.getEquipment();

		equipment.setHelmet(inv.getHelmet());
		equipment.setChestplate(inv.getChestplate());
		equipment.setLeggings(inv.getLeggings());
		equipment.setBoots(inv.getBoots());
		equipment.setItemInMainHand(inv.getItemInMainHand());
		equipment.setItemInOffHand(inv.getItemInOffHand());
	}

	private static Location computeCameraPosition(LivingEntity body) {
		return body.getEyeLocation().clone();
	}

	@Nullable
	private LivingEntity getBody(UUID uuid) {
		var entity = me.suxuan.hardcorezombies.HardcoreZombies.getInstance().getServer().getEntity(uuid);
		if (entity instanceof LivingEntity living) {
			return living;
		}
		return null;
	}

	private record DownedSession(
			UUID bodyUuid,
			Location bodyLocation,
			Location cameraAnchor,
			DownedAttributeLock savedAttributes
	) {
	}
}
