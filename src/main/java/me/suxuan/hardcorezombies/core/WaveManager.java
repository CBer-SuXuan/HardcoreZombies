package me.suxuan.hardcorezombies.core;

import me.suxuan.hardcorezombies.HardcoreZombies;
import me.suxuan.hardcorezombies.config.PluginConfig;
import me.suxuan.hardcorezombies.utils.PDCHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zombie;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class WaveManager {

	private final Arena arena;
	private int currentWave = 0;

	private int totalZombiesForWave;
	private int zombiesSpawned;
	private final Set<UUID> aliveZombies = new HashSet<>();

	private BukkitTask spawnTask;

	public WaveManager(Arena arena) {
		this.arena = arena;
	}

	public int getCurrentWave() {
		return currentWave;
	}

	public void startNextWave() {
		PluginConfig config = HardcoreZombies.getInstance().getPluginConfig();

		if (currentWave >= config.getVictoryWave()) {
			arena.endGame(true);
			return;
		}

		currentWave++;
		zombiesSpawned = 0;
		totalZombiesForWave = 5 + (currentWave * 2);

		arena.broadcastTitle(
				Component.text("第 " + currentWave + " 波", NamedTextColor.RED),
				Component.text("尸潮来袭...", NamedTextColor.GRAY)
		);

		startSpawning();
	}

	private void startSpawning() {
		if (spawnTask != null && !spawnTask.isCancelled()) {
			spawnTask.cancel();
		}

		PluginConfig config = HardcoreZombies.getInstance().getPluginConfig();
		long spawnInterval = Math.max(10L, 40L - (currentWave * 2L));
		int maxAlive = config.getMaxAliveZombies();

		spawnTask = new BukkitRunnable() {
			@Override
			public void run() {
				if (arena.getState() != GameState.IN_GAME || zombiesSpawned >= totalZombiesForWave) {
					this.cancel();
					return;
				}

				if (aliveZombies.size() >= maxAlive) {
					return;
				}

				spawnSingleZombie();
			}
		}.runTaskTimer(HardcoreZombies.getInstance(), 60L, spawnInterval);
	}

	private void spawnSingleZombie() {
		World world = arena.getWorld();
		if (world == null) return;

		List<Location> spawnNodes = getAvailableSpawnNodes();
		if (spawnNodes.isEmpty()) return;

		Location loc = spawnNodes.get(ThreadLocalRandom.current().nextInt(spawnNodes.size()));
		Zombie zombie = (Zombie) world.spawnEntity(loc, EntityType.ZOMBIE);

		PDCHelper.setString(zombie, PDCHelper.ARENA_ID_KEY, arena.getArenaId());

		aliveZombies.add(zombie.getUniqueId());
		zombiesSpawned++;
	}

	public void onZombieDeath(UUID zombieId) {
		if (aliveZombies.remove(zombieId)) {
			checkWaveEnd();
		}
	}

	private void checkWaveEnd() {
		if (zombiesSpawned >= totalZombiesForWave && aliveZombies.isEmpty()) {
			PluginConfig config = HardcoreZombies.getInstance().getPluginConfig();
			int delaySeconds = config.getBetweenWavesSeconds();

			arena.broadcast(Component.text(
					"本波防守成功！你有 " + delaySeconds + " 秒时间补给。",
					NamedTextColor.GREEN
			));

			new BukkitRunnable() {
				@Override
				public void run() {
					if (arena.getState() == GameState.IN_GAME) {
						startNextWave();
					}
				}
			}.runTaskLater(HardcoreZombies.getInstance(), delaySeconds * 20L);
		}
	}

	private List<Location> getAvailableSpawnNodes() {
		World world = arena.getWorld();
		if (world == null) return List.of();

		PluginConfig config = HardcoreZombies.getInstance().getPluginConfig();
		List<Vector> offsets = config.getSpawnOffsets(arena.getTemplateName());
		if (offsets.isEmpty()) {
			return List.of(world.getSpawnLocation());
		}

		Location base = world.getSpawnLocation();
		List<Location> nodes = new ArrayList<>();
		for (Vector offset : offsets) {
			nodes.add(base.clone().add(offset));
		}
		return nodes;
	}

	public void cleanup() {
		if (spawnTask != null && !spawnTask.isCancelled()) {
			spawnTask.cancel();
		}
		aliveZombies.clear();
	}
}
