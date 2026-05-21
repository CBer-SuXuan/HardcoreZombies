package me.suxuan.hardcorezombies.utils;

import me.suxuan.hardcorezombies.HardcoreZombies;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

public class PDCHelper {

	// 预定义的命名空间键（Key），用于标记和存储物品的核心属性
	public static final NamespacedKey WEAPON_ID_KEY = new NamespacedKey(HardcoreZombies.getInstance(), "weapon_id");
	public static final NamespacedKey AMMO_COUNT_KEY = new NamespacedKey(HardcoreZombies.getInstance(), "ammo_count");
	public static final NamespacedKey ITEM_MARKER_KEY = new NamespacedKey(HardcoreZombies.getInstance(), "hz_item");
	public static final NamespacedKey ARENA_ID_KEY = new NamespacedKey(HardcoreZombies.getInstance(), "arena_id");
	public static final NamespacedKey DOWNED_BODY_KEY = new NamespacedKey(HardcoreZombies.getInstance(), "downed_body");

	/**
	 * 为物品写入字符串类型的数据
	 *
	 * @param item  目标物品
	 * @param key   命名空间键
	 * @param value 写入的值
	 * @return 修改后的物品（支持链式调用）
	 */
	public static ItemStack setString(ItemStack item, NamespacedKey key, String value) {
		if (item == null || !item.hasItemMeta()) return item;
		ItemMeta meta = item.getItemMeta();
		meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
		item.setItemMeta(meta);
		return item;
	}

	/**
	 * 为实体写入字符串类型的数据
	 *
	 * @param entity 目标实体
	 * @param key    命名空间键
	 * @param value  写入的值
	 */
	public static void setString(Entity entity, NamespacedKey key, String value) {
		entity.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
	}

	/**
	 * 为物品写入整数类型的数据（例如：当前弹匣内剩余子弹数）
	 *
	 * @param item  目标物品
	 * @param key   命名空间键
	 * @param value 写入的值
	 * @return 修改后的物品
	 */
	public static ItemStack setInt(ItemStack item, NamespacedKey key, int value) {
		if (item == null || !item.hasItemMeta()) return item;
		ItemMeta meta = item.getItemMeta();
		meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, value);
		item.setItemMeta(meta);
		return item;
	}

	/**
	 * 为物品写入双精度浮点数数据（例如：后坐力、散布）
	 */
	public static ItemStack setDouble(ItemStack item, NamespacedKey key, double value) {
		if (item == null || !item.hasItemMeta()) return item;
		ItemMeta meta = item.getItemMeta();
		meta.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, value);
		item.setItemMeta(meta);
		return item;
	}

	/**
	 * 读取物品的字符串数据
	 *
	 * @param item 目标物品
	 * @param key  命名空间键
	 * @return 数据值，如果不存在则返回 null
	 */
	@Nullable
	public static String getString(ItemStack item, NamespacedKey key) {
		if (item == null || !item.hasItemMeta()) return null;
		PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
		return container.get(key, PersistentDataType.STRING);
	}

	/**
	 * 读取实体的字符串数据
	 *
	 * @param entity 目标实体
	 * @param key    命名空间键
	 * @return 数据值，如果不存在则返回 null
	 */
	@Nullable
	public static String getString(Entity entity, NamespacedKey key) {
		if (entity == null) return null;
		return entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
	}

	/**
	 * 读取物品的整数数据
	 *
	 * @param item 目标物品
	 * @param key  命名空间键
	 * @return 数据值，如果不存在则返回 null
	 */
	@Nullable
	public static Integer getInt(ItemStack item, NamespacedKey key) {
		if (item == null || !item.hasItemMeta()) return null;
		PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
		return container.get(key, PersistentDataType.INTEGER);
	}

	/**
	 * 读取物品的双精度浮点数数据
	 */
	@Nullable
	public static Double getDouble(ItemStack item, NamespacedKey key) {
		if (item == null || !item.hasItemMeta()) return null;
		PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
		return container.get(key, PersistentDataType.DOUBLE);
	}

	/**
	 * 检查物品是否具备特定的硬核模式标记
	 *
	 * @param item 目标物品
	 * @param key  命名空间键
	 * @return boolean 是否存在标记
	 */
	public static boolean hasMarker(ItemStack item, NamespacedKey key) {
		if (item == null || !item.hasItemMeta()) return false;
		PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
		return container.has(key);
	}
}