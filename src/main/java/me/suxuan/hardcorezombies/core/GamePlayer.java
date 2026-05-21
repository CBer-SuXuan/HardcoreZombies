package me.suxuan.hardcorezombies.core;

import me.suxuan.hardcorezombies.HardcoreZombies;
import me.suxuan.hardcorezombies.config.PluginConfig;
import me.suxuan.hardcorezombies.gameplay.DownedBodyManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public class GamePlayer {

	private final UUID uuid;
	private final Player bukkitPlayer;
	private final Arena arena;

	private int coins;
	private int kills;

	private boolean isDowned;
	private boolean isDead;

	private BukkitTask bleedTask;
	private int bleedSecondsLeft;

	public GamePlayer(Player player, Arena arena) {
		this.uuid = player.getUniqueId();
		this.bukkitPlayer = player;
		this.arena = arena;
		this.coins = 0;
		this.kills = 0;
		this.isDowned = false;
		this.isDead = false;
	}

	public Player getPlayer() {
		return bukkitPlayer;
	}

	public UUID getUuid() {
		return uuid;
	}

	public Arena getArena() {
		return arena;
	}

	public int getCoins() {
		return coins;
	}

	public void addCoins(int amount) {
		this.coins += amount;
		bukkitPlayer.sendMessage(Component.text("+" + amount + " 金币", NamedTextColor.GOLD));
	}

	public boolean spendCoins(int amount) {
		if (this.coins >= amount) {
			this.coins -= amount;
			return true;
		}
		bukkitPlayer.sendMessage(Component.text("金币不足！需要: " + amount, NamedTextColor.RED));
		return false;
	}

	public void addKill() {
		this.kills++;
	}

	public int getKills() {
		return kills;
	}

	public boolean isDowned() {
		return isDowned;
	}

	public boolean isDead() {
		return isDead;
	}

	public boolean isActiveCombatant() {
		return !isDead && !isDowned;
	}

	public void enterDownedState() {
		if (isDead || isDowned) return;

		isDowned = true;
		PluginConfig config = HardcoreZombies.getInstance().getPluginConfig();
		bleedSecondsLeft = config.getDownedBleedSeconds();

		bukkitPlayer.sendMessage(Component.text(
				"你已倒地！队友下蹲到你身边即可救援...",
				NamedTextColor.DARK_RED
		));

		if (bukkitPlayer.isDead()) {
			var loc = bukkitPlayer.getLocation();
			bukkitPlayer.spigot().respawn();
			bukkitPlayer.teleportAsync(loc);
		}

		bukkitPlayer.setGameMode(GameMode.SURVIVAL);
		applyDownedEffects();
		bukkitPlayer.setNoDamageTicks(40);

		var maxHealth = bukkitPlayer.getAttribute(Attribute.MAX_HEALTH);
		if (maxHealth != null) {
			bukkitPlayer.setHealth(Math.min(2.0, maxHealth.getValue()));
		}

		getDownedBodyManager().spawn(this);

		cancelBleedTask();
		bleedTask = new BukkitRunnable() {
			@Override
			public void run() {
				if (!bukkitPlayer.isOnline() || !isDowned) {
					cancel();
					return;
				}

				bleedSecondsLeft--;
				bukkitPlayer.sendActionBar(Component.text("流血中... " + bleedSecondsLeft + " 秒", NamedTextColor.DARK_RED));

				if (bleedSecondsLeft <= 0) {
					setDead(true);
					cancel();
				}
			}
		}.runTaskTimer(HardcoreZombies.getInstance(), 20L, 20L);
	}

	public void revive() {
		if (!isDowned || isDead) return;

		isDowned = false;
		cancelBleedTask();
		getDownedBodyManager().remove(this);
		clearDownedEffects();

		var maxHealth = bukkitPlayer.getAttribute(Attribute.MAX_HEALTH);
		if (maxHealth != null) {
			bukkitPlayer.setHealth(Math.max(4.0, maxHealth.getValue() * 0.5));
		}

		bukkitPlayer.sendMessage(Component.text("你已被拉起，继续战斗！", NamedTextColor.GREEN));
		arena.checkTeamWipe();
	}

	public void setDead(boolean dead) {
		if (isDead == dead) return;

		isDead = dead;
		if (dead) {
			cancelBleedTask();
			if (isDowned) {
				getDownedBodyManager().remove(this);
				isDowned = false;
			}
			clearDownedEffects();
			bukkitPlayer.setGameMode(GameMode.SPECTATOR);
			bukkitPlayer.sendMessage(Component.text("你已阵亡，只能观看剩余战斗。", NamedTextColor.DARK_GRAY));
			arena.checkTeamWipe();
		}
	}

	public void cleanup() {
		cancelBleedTask();
		if (isDowned) {
			getDownedBodyManager().remove(this);
		}
		clearDownedEffects();
		isDowned = false;
		isDead = false;
	}

	private void applyDownedEffects() {
		// 移动限制由 DownedAttributeLock 负责，此处仅保留隐身与虚弱
		bukkitPlayer.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, Integer.MAX_VALUE, 255, false, false, true));
		bukkitPlayer.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false, false));
	}

	private void clearDownedEffects() {
		bukkitPlayer.removePotionEffect(PotionEffectType.WEAKNESS);
		bukkitPlayer.removePotionEffect(PotionEffectType.INVISIBILITY);
	}

	private void cancelBleedTask() {
		if (bleedTask != null) {
			bleedTask.cancel();
			bleedTask = null;
		}
	}

	private static DownedBodyManager getDownedBodyManager() {
		return HardcoreZombies.getInstance().getDownedBodyManager();
	}
}
