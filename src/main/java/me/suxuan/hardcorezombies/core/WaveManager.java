package me.suxuan.hardcorezombies.core;

import me.suxuan.hardcorezombies.HardcoreZombies;
import me.suxuan.hardcorezombies.utils.PDCHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zombie;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class WaveManager {

	private final Arena arena;
	private int currentWave = 0;

	// 当前波次数据
	private int totalZombiesForWave;
	private int zombiesSpawned;
	private final Set<UUID> aliveZombies = new HashSet<>();

	private BukkitTask spawnTask;

	public WaveManager(Arena arena) {
		this.arena = arena;
	}

	/**
	 * 开启下一波
	 */
	public void startNextWave() {
		currentWave++;
		zombiesSpawned = 0;

		// 硬核数值计算：基础 5 只，每波增加 (当前波次 * 2) 只
		totalZombiesForWave = 5 + (currentWave * 2);

		arena.broadcastTitle(
				Component.text("第 " + currentWave + " 波", NamedTextColor.RED),
				Component.text("尸潮来袭...", NamedTextColor.GRAY)
		);

		startSpawning();
	}

	/**
	 * 启动刷怪任务
	 */
	private void startSpawning() {
		// 取消旧任务防止重叠
		if (spawnTask != null && !spawnTask.isCancelled()) {
			spawnTask.cancel();
		}

		// 每波出怪间隔递减，越往后刷怪越快，体现硬核压迫感 (最低限制 10 tick)
		long spawnInterval = Math.max(10L, 40L - (currentWave * 2L));

		spawnTask = new BukkitRunnable() {
			@Override
			public void run() {
				// 如果房间状态不对，或者已经刷够了总数，停止任务
				if (arena.getState() != GameState.IN_GAME || zombiesSpawned >= totalZombiesForWave) {
					this.cancel();
					return;
				}

				// TODO: 限制同屏最大怪物数量（例如不能超过 30 只），避免服务器压力过大
				if (aliveZombies.size() >= 30) {
					return; // 暂缓生成
				}

				spawnSingleZombie();
			}
		}.runTaskTimer(HardcoreZombies.getInstance(), 60L, spawnInterval); // 延迟 3 秒开始
	}

	private void spawnSingleZombie() {
		List<Location> spawnNodes = getAvailableSpawnNodes();
		if (spawnNodes.isEmpty()) return;

		Location loc = spawnNodes.get(ThreadLocalRandom.current().nextInt(spawnNodes.size()));
		Zombie zombie = (Zombie) arena.getWorld().spawnEntity(loc, EntityType.ZOMBIE);

		// 【核心新增】：给僵尸烙印上当前房间的 ID
		PDCHelper.setString(zombie, PDCHelper.ARENA_ID_KEY, arena.getArenaId());

		aliveZombies.add(zombie.getUniqueId());
		zombiesSpawned++;
	}

	/**
	 * 当实体死亡时调用，检查是否推进波次
	 */
	public void onZombieDeath(UUID zombieId) {
		if (aliveZombies.remove(zombieId)) {
			checkWaveEnd();
		}
	}

	private void checkWaveEnd() {
		// 如果这波的怪都刷完了，并且场上存活的怪为 0
		if (zombiesSpawned >= totalZombiesForWave && aliveZombies.isEmpty()) {
			arena.broadcast(Component.text("本波防守成功！你有 10 秒时间补给。", NamedTextColor.GREEN));

			// 延迟 10 秒开启下一波
			new BukkitRunnable() {
				@Override
				public void run() {
					if (arena.getState() == GameState.IN_GAME) {
						startNextWave();
					}
				}
			}.runTaskLater(HardcoreZombies.getInstance(), 200L);
		}
	}

	/**
	 * 获取可用刷怪点的占位方法
	 */
	private List<Location> getAvailableSpawnNodes() {
		// 临时返回世界出生点作为测试
		return List.of(arena.getWorld().getSpawnLocation());
	}

	public void cleanup() {
		if (spawnTask != null && !spawnTask.isCancelled()) {
			spawnTask.cancel();
		}
		aliveZombies.clear();
	}
}