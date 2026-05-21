package me.suxuan.hardcorezombies.gameplay;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

/**
 * 通过 Attribute 与移速字段限制倒地玩家位移（不依赖循环传送）。
 */
public final class DownedAttributeLock {

	private final double movementSpeed;
	private final double flyingSpeed;
	private final double jumpStrength;
	private final double gravity;
	private final double stepHeight;
	private final float walkSpeed;
	private final float flySpeed;

	private DownedAttributeLock(
			double movementSpeed,
			double flyingSpeed,
			double jumpStrength,
			double gravity,
			double stepHeight,
			float walkSpeed,
			float flySpeed
	) {
		this.movementSpeed = movementSpeed;
		this.flyingSpeed = flyingSpeed;
		this.jumpStrength = jumpStrength;
		this.gravity = gravity;
		this.stepHeight = stepHeight;
		this.walkSpeed = walkSpeed;
		this.flySpeed = flySpeed;
	}

	public static DownedAttributeLock apply(Player player) {
		DownedAttributeLock saved = capture(player);

		setBaseValue(player, Attribute.MOVEMENT_SPEED, 0);
		setBaseValue(player, Attribute.FLYING_SPEED, 0);
		setBaseValue(player, Attribute.JUMP_STRENGTH, 0);
		setBaseValue(player, Attribute.GRAVITY, 0);
		setBaseValue(player, Attribute.STEP_HEIGHT, 0);

		player.setWalkSpeed(0f);
		player.setFlySpeed(0f);

		return saved;
	}

	public void restore(Player player) {
		setBaseValue(player, Attribute.MOVEMENT_SPEED, movementSpeed);
		setBaseValue(player, Attribute.FLYING_SPEED, flyingSpeed);
		setBaseValue(player, Attribute.JUMP_STRENGTH, jumpStrength);
		setBaseValue(player, Attribute.GRAVITY, gravity);
		setBaseValue(player, Attribute.STEP_HEIGHT, stepHeight);

		player.setWalkSpeed(walkSpeed);
		player.setFlySpeed(flySpeed);
	}

	private static DownedAttributeLock capture(Player player) {
		return new DownedAttributeLock(
				getBaseValue(player, Attribute.MOVEMENT_SPEED, 0.1),
				getBaseValue(player, Attribute.FLYING_SPEED, 0.4),
				getBaseValue(player, Attribute.JUMP_STRENGTH, 0.42),
				getBaseValue(player, Attribute.GRAVITY, 0.08),
				getBaseValue(player, Attribute.STEP_HEIGHT, 0.6),
				player.getWalkSpeed(),
				player.getFlySpeed()
		);
	}

	private static double getBaseValue(Player player, Attribute attribute, double fallback) {
		AttributeInstance instance = player.getAttribute(attribute);
		return instance != null ? instance.getBaseValue() : fallback;
	}

	private static void setBaseValue(Player player, Attribute attribute, double value) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}
}
