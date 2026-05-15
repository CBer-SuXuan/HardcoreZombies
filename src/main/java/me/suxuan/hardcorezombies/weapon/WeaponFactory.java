package me.suxuan.hardcorezombies.weapon;

import me.suxuan.hardcorezombies.HardcoreZombies;
import me.suxuan.hardcorezombies.utils.PDCHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

public class WeaponFactory {

	public static final NamespacedKey MAX_AMMO_KEY = new NamespacedKey(HardcoreZombies.getInstance(), "max_ammo");  // 弹匣上限
	public static final NamespacedKey RESERVE_AMMO_KEY = new NamespacedKey(HardcoreZombies.getInstance(), "reserve_ammo");  // 备弹数
	public static final NamespacedKey DAMAGE_KEY = new NamespacedKey(HardcoreZombies.getInstance(), "damage");  // 伤害值
	public static final NamespacedKey FIRE_RATE_KEY = new NamespacedKey(HardcoreZombies.getInstance(), "fire_rate"); // 射击冷却(毫秒)
	public static final NamespacedKey PARTICLE_RGB_KEY = new NamespacedKey(HardcoreZombies.getInstance(), "particle_rgb"); // 格式: "R,G,B"，粒子颜色
	public static final NamespacedKey PELLETS_KEY = new NamespacedKey(HardcoreZombies.getInstance(), "pellets"); // 弹片数量(默认1)

	public static final NamespacedKey SPREAD_KEY = new NamespacedKey(HardcoreZombies.getInstance(), "spread"); // 散布程度(默认0.0)
	public static final NamespacedKey RECOIL_KEY = new NamespacedKey(HardcoreZombies.getInstance(), "recoil"); // 后坐力(默认0.0)

	public static final NamespacedKey SPECIAL_TRAIT_KEY = new NamespacedKey(HardcoreZombies.getInstance(), "trait"); // 特殊效果标识

	public static final NamespacedKey RELOAD_TICKS_KEY = new NamespacedKey(HardcoreZombies.getInstance(), "reload_ticks"); // 换弹时间(Tick)
	public static final NamespacedKey RELOAD_SOUND_START_KEY = new NamespacedKey(HardcoreZombies.getInstance(), "reload_sound_start"); // 拔弹匣音效
	public static final NamespacedKey RELOAD_SOUND_FINISH_KEY = new NamespacedKey(HardcoreZombies.getInstance(), "reload_sound_finish"); // 上膛音效

	public static final NamespacedKey COIN_HIT_KEY = new NamespacedKey(HardcoreZombies.getInstance(), "coin_hit"); // 普通命中金币
	public static final NamespacedKey COIN_HEADSHOT_KEY = new NamespacedKey(HardcoreZombies.getInstance(), "coin_headshot"); // 爆头命中金币

	public static ItemStack createM4A1() {
		return new WeaponBuilder("m4a1", "M4A1 突击步枪", Material.IRON_HOE)
				.nameColor(NamedTextColor.AQUA)
				.ammo(30, 60)
				.damage(8)
				.fireRate(100)
				.recoil(1.0)
				.spread(0.02)
				.particleRGB("255,255,200")
				.reload(40, "ITEM_FLINTANDSTEEL_USE", "BLOCK_IRON_DOOR_CLOSE")
				.coins(8, 10)
				.build();
	}

	public static ItemStack createS686() {
		return new WeaponBuilder("s686", "S686 龙息霰弹枪", Material.GOLDEN_HOE)
				.nameColor(NamedTextColor.RED)
				.ammo(5, 100)
				.damage(2)
				.fireRate(500)
				.recoil(4.0)
				.spread(0.15)
				.pellets(6)
				.particleRGB("255,50,0")
				.trait("fire")
				.reload(60, "BLOCK_WOODEN_DOOR_OPEN", "BLOCK_WOODEN_TRAPDOOR_CLOSE")
				.coins(10, 15)
				.build();
	}

	public static ItemStack createDesertEagle() {
		return new WeaponBuilder("deagle", "沙漠之鹰", Material.DIAMOND_HOE)
				.nameColor(NamedTextColor.GOLD)
				.ammo(8, 300)
				.damage(4)
				.fireRate(500)
				.recoil(1.5)
				.spread(0.01)
				.reload(20, "BLOCK_WOODEN_DOOR_OPEN", "BLOCK_WOODEN_TRAPDOOR_CLOSE")
				.coins(15, 20)
				.build();
	}

	public static class WeaponBuilder {
		private final String id;
		private final String displayName;
		private final Material material;

		// 默认属性
		private TextColor nameColor = NamedTextColor.WHITE;
		private int maxAmmo = 30;
		private int reserveAmmo = 90;
		private int damage = 10;
		private int fireRate = 200;
		private double recoil = 1.5;
		private double spread = 0.0;
		private int pellets = 1;
		private String particleRgb = "255,255,255";
		private String trait = "none";
		private int reloadTicks = 40;
		private String reloadStartSound = "ITEM_FLINTANDSTEEL_USE";
		private String reloadFinishSound = "BLOCK_IRON_DOOR_CLOSE";
		private int coinHit = 10;
		private int coinHeadshot = 20;

		public WeaponBuilder(String id, String displayName, Material material) {
			this.id = id;
			this.displayName = displayName;
			this.material = material;
		}

		public WeaponBuilder nameColor(TextColor color) {
			this.nameColor = color;
			return this;
		}

		public WeaponBuilder ammo(int max, int reserve) {
			this.maxAmmo = max;
			this.reserveAmmo = reserve;
			return this;
		}

		public WeaponBuilder damage(int dmg) {
			this.damage = dmg;
			return this;
		}

		public WeaponBuilder fireRate(int rate) {
			this.fireRate = rate;
			return this;
		}

		public WeaponBuilder recoil(double rec) {
			this.recoil = rec;
			return this;
		}

		public WeaponBuilder spread(double spr) {
			this.spread = spr;
			return this;
		}

		public WeaponBuilder pellets(int pel) {
			this.pellets = pel;
			return this;
		}

		public WeaponBuilder particleRGB(String rgb) {
			this.particleRgb = rgb;
			return this;
		}

		public WeaponBuilder trait(String tr) {
			this.trait = tr;
			return this;
		}

		public WeaponBuilder reload(int ticks, String startSound, String finishSound) {
			this.reloadTicks = ticks;
			this.reloadStartSound = startSound;
			this.reloadFinishSound = finishSound;
			return this;
		}

		public WeaponBuilder coins(int hit, int headshot) {
			this.coinHit = hit;
			this.coinHeadshot = headshot;
			return this;
		}

		/**
		 * 最终构建并封装全部 PDC 数据
		 */
		public ItemStack build() {
			ItemStack item = new ItemStack(material);
			ItemMeta meta = item.getItemMeta();
			if (meta != null) {
				meta.displayName(Component.text(displayName, nameColor));
				meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
				meta.setMaxStackSize(99);
				if (meta instanceof Damageable damageable) {
					damageable.setMaxDamage(100);
					damageable.setDamage(0);
				}
				item.setItemMeta(meta);
			}

			// 写入基础标记
			item = PDCHelper.setString(item, PDCHelper.WEAPON_ID_KEY, id);
			item = PDCHelper.setString(item, PDCHelper.ITEM_MARKER_KEY, displayName);

			// 写入弹药数据
			item = PDCHelper.setInt(item, PDCHelper.AMMO_COUNT_KEY, maxAmmo);
			item = PDCHelper.setInt(item, MAX_AMMO_KEY, maxAmmo);
			item = PDCHelper.setInt(item, RESERVE_AMMO_KEY, reserveAmmo);

			// 写入战斗属性
			item = PDCHelper.setInt(item, DAMAGE_KEY, damage);
			item = PDCHelper.setInt(item, FIRE_RATE_KEY, fireRate);
			item = PDCHelper.setInt(item, PELLETS_KEY, pellets);
			item = PDCHelper.setDouble(item, SPREAD_KEY, spread);
			item = PDCHelper.setDouble(item, RECOIL_KEY, recoil); // 写入后坐力

			// 写入视觉与特殊属性
			item = PDCHelper.setString(item, PARTICLE_RGB_KEY, particleRgb);
			item = PDCHelper.setString(item, SPECIAL_TRAIT_KEY, trait);

			item = PDCHelper.setInt(item, RELOAD_TICKS_KEY, reloadTicks);
			item = PDCHelper.setString(item, RELOAD_SOUND_START_KEY, reloadStartSound);
			item = PDCHelper.setString(item, RELOAD_SOUND_FINISH_KEY, reloadFinishSound);

			item = PDCHelper.setInt(item, COIN_HIT_KEY, coinHit);
			item = PDCHelper.setInt(item, COIN_HEADSHOT_KEY, coinHeadshot);

			item.setAmount(Math.max(1, maxAmmo));

			return item;
		}
	}
}