package me.suxuan.hardcorezombies.weapon;

import me.suxuan.hardcorezombies.HardcoreZombies;
import me.suxuan.hardcorezombies.config.PluginConfig;
import me.suxuan.hardcorezombies.core.Arena;
import me.suxuan.hardcorezombies.core.GamePlayer;
import me.suxuan.hardcorezombies.core.GameRoomManager;
import me.suxuan.hardcorezombies.core.GameState;
import me.suxuan.hardcorezombies.utils.PDCHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class WeaponListener implements Listener {

	private final GameRoomManager roomManager;

	private final Map<UUID, BukkitTask> reloadTasks = new ConcurrentHashMap<>();
	private final Map<UUID, Long> shootCooldowns = new ConcurrentHashMap<>();

	public WeaponListener(GameRoomManager roomManager) {
		this.roomManager = roomManager;
		startGlobalUITask();
	}

	private void startGlobalUITask() {
		new BukkitRunnable() {
			@Override
			public void run() {
				for (Player player : Bukkit.getOnlinePlayers()) {
					if (reloadTasks.containsKey(player.getUniqueId()) || isCombatBlocked(player)) continue;

					ItemStack item = player.getInventory().getItemInMainHand();
					if (!PDCHelper.hasMarker(item, PDCHelper.WEAPON_ID_KEY)) continue;

					Integer fireRate = PDCHelper.getInt(item, WeaponFactory.FIRE_RATE_KEY);
					if (fireRate == null) fireRate = 200;

					long lastShot = shootCooldowns.getOrDefault(player.getUniqueId(), 0L);
					long elapsed = System.currentTimeMillis() - lastShot;

					float progress = (float) elapsed / fireRate;
					if (progress > 1.0f) progress = 1.0f;

					player.setExp(progress);
				}
			}
		}.runTaskTimer(HardcoreZombies.getInstance(), 0L, 1L);
	}

	@EventHandler
	public void onPlayerDropWeapon(PlayerDropItemEvent event) {
		if (PDCHelper.hasMarker(event.getItemDrop().getItemStack(), PDCHelper.WEAPON_ID_KEY)) {
			event.setCancelled(true);
			if (isCombatBlocked(event.getPlayer())) return;
			attemptReload(event.getPlayer(), event.getItemDrop().getItemStack());
		}
	}

	@EventHandler
	public void onPlayerChangeWeapon(PlayerItemHeldEvent event) {
		Player player = event.getPlayer();
		if (isCombatBlocked(player)) {
			event.setCancelled(true);
			return;
		}

		if (reloadTasks.containsKey(player.getUniqueId())) {
			reloadTasks.get(player.getUniqueId()).cancel();
			reloadTasks.remove(player.getUniqueId());
			player.sendActionBar(Component.text("换弹被打断！", NamedTextColor.RED));

			ItemStack oldItem = player.getInventory().getItem(event.getPreviousSlot());
			if (oldItem != null && oldItem.hasItemMeta()) {
				ItemMeta oldMeta = oldItem.getItemMeta();
				if (oldMeta instanceof org.bukkit.inventory.meta.Damageable damageable) {
					Integer oldAmmo = PDCHelper.getInt(oldItem, PDCHelper.AMMO_COUNT_KEY);
					Integer oldReserve = PDCHelper.getInt(oldItem, WeaponFactory.RESERVE_AMMO_KEY);
					if (oldAmmo != null && oldAmmo <= 0 && oldReserve != null && oldReserve <= 0) {
						damageable.setDamage(100);
					} else {
						damageable.setDamage(0);
					}
					oldItem.setItemMeta(oldMeta);
				}
			}
		}

		ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
		if (newItem != null && newItem.hasItemMeta() && PDCHelper.hasMarker(newItem, PDCHelper.WEAPON_ID_KEY)) {
			updateAmmoDisplay(player, newItem);

			Integer currentAmmo = PDCHelper.getInt(newItem, PDCHelper.AMMO_COUNT_KEY);
			Integer reserveAmmo = PDCHelper.getInt(newItem, WeaponFactory.RESERVE_AMMO_KEY);

			if (currentAmmo != null && currentAmmo <= 0) {
				if (reserveAmmo != null && reserveAmmo > 0) {
					attemptReload(player, newItem);
				} else {
					ItemMeta meta = newItem.getItemMeta();
					if (meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
						damageable.setDamage(100);
						newItem.setItemMeta(damageable);
					}
				}
			}
		} else {
			player.setLevel(0);
			player.setExp(0f);
			player.sendActionBar(Component.empty());
		}
	}

	@EventHandler
	public void onPlayerWeaponInteract(PlayerInteractEvent event) {
		Player player = event.getPlayer();
		ItemStack item = event.getItem();

		if (!PDCHelper.hasMarker(item, PDCHelper.WEAPON_ID_KEY)) return;
		event.setCancelled(true);

		if (isCombatBlocked(player)) return;
		if (reloadTasks.containsKey(player.getUniqueId())) return;

		if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
			attemptReload(player, item);
			return;
		}

		if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
			long currentTime = System.currentTimeMillis();
			long lastShot = shootCooldowns.getOrDefault(player.getUniqueId(), 0L);
			Integer fireRate = PDCHelper.getInt(item, WeaponFactory.FIRE_RATE_KEY);
			int requiredCooldown = (fireRate != null) ? fireRate : 200;

			if (currentTime - lastShot >= requiredCooldown) {
				handleShooting(player, item);
			}
		}
	}

	public void clearPlayerWeaponState(Player player) {
		BukkitTask task = reloadTasks.remove(player.getUniqueId());
		if (task != null) {
			task.cancel();
		}
		shootCooldowns.remove(player.getUniqueId());
		player.setExp(0f);
		player.sendActionBar(Component.empty());
	}

	private void attemptReload(Player player, ItemStack weapon) {
		if (isCombatBlocked(player)) return;

		Integer currentAmmo = PDCHelper.getInt(weapon, PDCHelper.AMMO_COUNT_KEY);
		Integer maxAmmo = PDCHelper.getInt(weapon, WeaponFactory.MAX_AMMO_KEY);
		Integer reserveAmmo = PDCHelper.getInt(weapon, WeaponFactory.RESERVE_AMMO_KEY);

		if (currentAmmo == null || maxAmmo == null) return;

		if (currentAmmo.equals(maxAmmo) || (reserveAmmo != null && reserveAmmo <= currentAmmo)) {
			player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1.0f, 0.5f);
			return;
		}

		final int targetAmmo = (reserveAmmo != null) ? Math.min(maxAmmo, reserveAmmo) : maxAmmo;

		Integer customTicks = PDCHelper.getInt(weapon, WeaponFactory.RELOAD_TICKS_KEY);
		final int reloadTimeTicks = (customTicks != null && customTicks > 0) ? customTicks : 40;

		Sound startSound = parseSound(PDCHelper.getString(weapon, WeaponFactory.RELOAD_SOUND_START_KEY), Sound.ITEM_FLINTANDSTEEL_USE);
		Sound finishSound = parseSound(PDCHelper.getString(weapon, WeaponFactory.RELOAD_SOUND_FINISH_KEY), Sound.BLOCK_IRON_DOOR_CLOSE);

		// 播放动态的起始音效
		player.playSound(player.getLocation(), startSound, 1.0f, 1.0f);
		player.sendActionBar(Component.text("正在换弹...", NamedTextColor.YELLOW));
		player.setExp(0f);

		BukkitTask task = new BukkitRunnable() {
			int ticks = 0;

			@Override
			public void run() {
				if (!player.isOnline() || player.isDead() || isCombatBlocked(player)) {
					reloadTasks.remove(player.getUniqueId());
					this.cancel();
					return;
				}

				ItemStack mainHandItem = player.getInventory().getItemInMainHand();
				if (!PDCHelper.hasMarker(mainHandItem, PDCHelper.WEAPON_ID_KEY)) {
					reloadTasks.remove(player.getUniqueId());
					this.cancel();
					return;
				}

				ticks++;

				if (ticks == reloadTimeTicks / 2) {
					player.playSound(player.getLocation(), Sound.BLOCK_PISTON_CONTRACT, 0.5f, 1.5f);
				}

				ItemMeta currentMeta = mainHandItem.getItemMeta();
				if (currentMeta instanceof Damageable damageable) {
					double progress = (double) ticks / reloadTimeTicks;
					int currentDamage = (int) (100 * (1.0 - progress));
					damageable.setDamage(currentDamage);
					mainHandItem.setItemMeta(damageable);
					player.getInventory().setItemInMainHand(mainHandItem);
				}

				if (ticks >= reloadTimeTicks) {
					ItemMeta finalMeta = mainHandItem.getItemMeta();
					if (finalMeta instanceof org.bukkit.inventory.meta.Damageable damageable) {
						damageable.setDamage(0);
						mainHandItem.setItemMeta(damageable);
					}

					PDCHelper.setInt(mainHandItem, PDCHelper.AMMO_COUNT_KEY, targetAmmo);

					updateAmmoDisplay(player, mainHandItem);

					player.getInventory().setItemInMainHand(mainHandItem);

					player.playSound(player.getLocation(), finishSound, 1.0f, 2.0f);
					reloadTasks.remove(player.getUniqueId());
					this.cancel();
				}
			}
		}.runTaskTimer(HardcoreZombies.getInstance(), 0L, 1L);
		reloadTasks.put(player.getUniqueId(), task);
	}

	private void handleShooting(Player player, ItemStack item) {
		if (isCombatBlocked(player)) return;

		Integer currentAmmo = PDCHelper.getInt(item, PDCHelper.AMMO_COUNT_KEY);
		Integer damage = PDCHelper.getInt(item, WeaponFactory.DAMAGE_KEY);
		Integer reserveAmmo = PDCHelper.getInt(item, WeaponFactory.RESERVE_AMMO_KEY); // 修复了拼写错误
		if (currentAmmo == null || damage == null) return;

		if (currentAmmo <= 0) {
			attemptReload(player, item);
			return;
		}

		shootCooldowns.put(player.getUniqueId(), System.currentTimeMillis());

		currentAmmo--;
		PDCHelper.setInt(item, PDCHelper.AMMO_COUNT_KEY, currentAmmo);

		if (reserveAmmo != null && reserveAmmo > 0) {
			reserveAmmo--;
			PDCHelper.setInt(item, WeaponFactory.RESERVE_AMMO_KEY, reserveAmmo);
		}

		updateAmmoDisplay(player, item);

		player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.5f);

		Double recoil = PDCHelper.getDouble(item, WeaponFactory.RECOIL_KEY);
		if (recoil != null && recoil > 0) {
			player.setRotation(player.getLocation().getYaw(), player.getLocation().getPitch() - recoil.floatValue());
		}

		Integer pellets = PDCHelper.getInt(item, WeaponFactory.PELLETS_KEY);
		if (pellets == null) pellets = 1;

		Double spread = PDCHelper.getDouble(item, WeaponFactory.SPREAD_KEY);
		if (spread == null) spread = 0.0;

		String rgbStr = PDCHelper.getString(item, WeaponFactory.PARTICLE_RGB_KEY);
		Color particleColor = parseColor(rgbStr);

		String trait = PDCHelper.getString(item, WeaponFactory.SPECIAL_TRAIT_KEY);

		Location eyeLoc = player.getEyeLocation();
		ThreadLocalRandom random = ThreadLocalRandom.current();

		for (int i = 0; i < pellets; i++) {
			Vector direction = eyeLoc.getDirection();

			if (spread > 0) {
				direction.add(new Vector(
						random.nextGaussian() * spread,
						random.nextGaussian() * spread,
						random.nextGaussian() * spread
				)).normalize();
			}

			RayTraceResult rayTraceResult = player.getWorld().rayTrace(
					eyeLoc, direction, 30.0,
					FluidCollisionMode.NEVER, true, 0.1,
					entity -> entity != player && entity instanceof org.bukkit.entity.Damageable
			);

			double distance = rayTraceResult != null ? rayTraceResult.getHitPosition().distance(eyeLoc.toVector()) : 30.0;
			drawBulletTrail(eyeLoc, direction, distance, particleColor);

			if (rayTraceResult != null && rayTraceResult.getHitEntity() instanceof org.bukkit.entity.Damageable target) {
				if (roomManager.isFriendlyCombatTarget(player, target)) {
					continue;
				}

				double finalDamage = damage;
				boolean isHeadshot = isHeadshot(rayTraceResult, target);
				if (isHeadshot) {
					double headshotMult = resolveHeadshotMultiplier(item);
					finalDamage *= headshotMult;
					player.playSound(player.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f, 1.0f);
					player.sendActionBar(Component.text(
							"爆头! x" + String.format("%.1f", headshotMult) + " (" + (int) Math.ceil(finalDamage) + " 伤害)",
							NamedTextColor.GOLD
					));
				}

				if ("fire".equals(trait)) {
					target.setFireTicks(60);
				}

				Arena arena = roomManager.getPlayerArena(player);
				if (arena != null) {
					GamePlayer gamePlayer = arena.getGamePlayer(player);
					if (gamePlayer != null && !gamePlayer.isDead()) {
						Integer hitCoin = PDCHelper.getInt(item, WeaponFactory.COIN_HIT_KEY);
						if (hitCoin == null) hitCoin = 10;

						Integer headshotCoin = PDCHelper.getInt(item, WeaponFactory.COIN_HEADSHOT_KEY);
						if (headshotCoin == null) headshotCoin = 20;

						int coinsEarned = isHeadshot ? headshotCoin : hitCoin;
						gamePlayer.addCoins(coinsEarned);
					}
				}

				target.damage(finalDamage, player);
				target.getWorld().spawnParticle(Particle.BLOCK, rayTraceResult.getHitPosition().toLocation(target.getWorld()), 10, 0.1, 0.1, 0.1, org.bukkit.Material.REDSTONE_BLOCK.createBlockData());
			}
		}

		// 如果开完这枪刚好没子弹了，自动触发一次换弹
		if (currentAmmo <= 0) {
			if (reserveAmmo != null && reserveAmmo <= 0) {
				// 彻底打空了，将耐久拉满以显示红色空条
				ItemMeta meta = item.getItemMeta();
				if (meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
					damageable.setDamage(100);
					item.setItemMeta(damageable);
				}
				player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1.0f, 0.5f);
				updateAmmoDisplay(player, item);
			} else {
				// 还有备弹，触发自动换弹
				attemptReload(player, item);
			}
		}
	}

	private void updateAmmoDisplay(Player player, ItemStack weapon) {
		Integer currentAmmo = PDCHelper.getInt(weapon, PDCHelper.AMMO_COUNT_KEY);
		Integer reserveAmmo = PDCHelper.getInt(weapon, WeaponFactory.RESERVE_AMMO_KEY);

		if (currentAmmo == null) return;

		player.setLevel(Objects.requireNonNullElse(reserveAmmo, 999));

		int displayAmount = Math.max(1, currentAmmo);
		weapon.setAmount(displayAmount);

		if (currentAmmo == 0) {
			if (reserveAmmo != null && reserveAmmo <= 0) {
				// 彻底没子弹时的提示
				player.sendActionBar(Component.text("弹药已彻底耗尽！需要补给！", NamedTextColor.DARK_RED));
			} else {
				// 正常换弹时的提示
				player.sendActionBar(Component.text("自动装填中...", NamedTextColor.RED));
			}
		}
	}

	private Color parseColor(String rgbStr) {
		if (rgbStr == null || rgbStr.isEmpty()) return Color.fromRGB(255, 255, 200);
		try {
			String[] parts = rgbStr.split(",");
			return Color.fromRGB(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
		} catch (Exception e) {
			return Color.fromRGB(255, 255, 200);
		}
	}

	private Sound parseSound(String soundName, Sound defaultSound) {
		if (soundName == null || soundName.isEmpty()) return defaultSound;
		String normalized = soundName.trim();

		String lowered = normalized.toLowerCase(Locale.ROOT);
		NamespacedKey key = NamespacedKey.fromString(lowered);
		if (key != null) {
			Sound sound = Registry.SOUNDS.get(key);
			if (sound != null) {
				return sound;
			}
		}

		// 兼容旧格式（如 ENTITY_ZOMBIE_AMBIENT）=> entity.zombie.ambient
		if (!lowered.contains(":")) {
			NamespacedKey legacyKey = NamespacedKey.minecraft(lowered.replace('_', '.'));
			Sound legacySound = Registry.SOUNDS.get(legacyKey);
			if (legacySound != null) {
				return legacySound;
			}
		}

		return defaultSound; // 如果拼写错误，回退到默认音效
	}

	private void drawBulletTrail(Location start, Vector direction, double distance, Color color) {
		Particle.DustOptions dustOptions = new Particle.DustOptions(color, 0.6f);
		for (double d = 0; d < distance; d += 0.5) {
			Location particleLoc = start.clone().add(direction.clone().multiply(d));
			start.getWorld().spawnParticle(Particle.DUST, particleLoc, 1, 0, 0, 0, 0, dustOptions);
		}
	}

	private double resolveHeadshotMultiplier(ItemStack item) {
		Double weaponMult = PDCHelper.getDouble(item, WeaponFactory.HEADSHOT_DAMAGE_MULT_KEY);
		if (weaponMult != null && weaponMult > 0) {
			return weaponMult;
		}
		PluginConfig config = HardcoreZombies.getInstance().getPluginConfig();
		return config.getHeadshotDamageMultiplier();
	}

	private boolean isCombatBlocked(Player player) {
		Arena arena = roomManager.getPlayerArena(player);
		if (arena == null || arena.getState() != GameState.IN_GAME) {
			return false;
		}
		GamePlayer gp = arena.getGamePlayer(player);
		return gp != null && (gp.isDowned() || gp.isDead());
	}

	private boolean isHeadshot(RayTraceResult result, Entity target) {
		double hitY = result.getHitPosition().getY();
		double headThreshold;
		if (target instanceof LivingEntity living) {
			headThreshold = living.getEyeLocation().getY() - 0.15;
		} else {
			headThreshold = target.getLocation().getY() + target.getHeight() * 0.75;
		}
		return hitY >= headThreshold;
	}
}