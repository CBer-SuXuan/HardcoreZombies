package me.suxuan.hardcorezombies.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.UUID;

public class GamePlayer {

	private final UUID uuid;
	private final Player bukkitPlayer;
	private final Arena arena;

	// 局内经济与统计
	private int coins;
	private int kills;

	// 硬核机制：是否处于倒地流血状态
	private boolean isDowned;
	// 彻底死亡（只能OB等待下一回合或游戏结束）
	private boolean isDead;

	public GamePlayer(Player player, Arena arena) {
		this.uuid = player.getUniqueId();
		this.bukkitPlayer = player;
		this.arena = arena;
		this.coins = 0; // 初始启动资金
		this.kills = 0;
		this.isDowned = false;
		this.isDead = false;
	}

	public Player getPlayer() {
		return bukkitPlayer;
	}

	public int getCoins() {
		return coins;
	}

	public void addCoins(int amount) {
		this.coins += amount;
		// 使用 Paper Component 在 Action Bar 提示金币增加
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

	public boolean isDowned() {
		return isDowned;
	}

	public void setDowned(boolean downed) {
		this.isDowned = downed;
		if (downed) {
			bukkitPlayer.sendMessage(Component.text("你已倒地！等待队友救援...", NamedTextColor.DARK_RED));
			// TODO: 给玩家施加缓慢、失明等 Debuff，并启动流血倒计时任务
		} else {
			bukkitPlayer.sendMessage(Component.text("你已被拉起，继续战斗！", NamedTextColor.GREEN));
			// TODO: 清除 Debuff
		}
	}

	public boolean isDead() {
		return isDead;
	}

	public void setDead(boolean dead) {
		this.isDead = dead;
		if (dead) {
			bukkitPlayer.setGameMode(org.bukkit.GameMode.SPECTATOR);
			bukkitPlayer.sendMessage(Component.text("你已阵亡，只能观看剩余战斗。", NamedTextColor.DARK_GRAY));
		}
	}
}