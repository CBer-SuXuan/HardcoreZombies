package me.suxuan.hardcorezombies.config;

import lombok.Getter;
import me.suxuan.hardcorezombies.HardcoreZombies;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class PluginConfig {

	private final HardcoreZombies plugin;

	private Location lobbyLocation;
	private int minPlayers;
	private int maxPlayers;
	private int startCountdownSeconds;
	private int betweenWavesSeconds;
	private int victoryWave;
	private int endDelaySeconds;
	private int maxAliveZombies;
	private int downedBleedSeconds;
	private int reviveChannelTicks;
	private double reviveRange;
	private int victoryBonus;
	private int perWaveSurvived;
	private int perKill;
	private double headshotDamageMultiplier;
	private boolean npcJoinOnly;
	private String autoJoinTemplate;

	private final Map<String, List<Vector>> templateSpawnOffsets = new HashMap<>();

	public PluginConfig(HardcoreZombies plugin) {
		this.plugin = plugin;
	}

	public void load() {
		plugin.saveDefaultConfig();
		plugin.reloadConfig();
		FileConfiguration config = plugin.getConfig();

		String lobbyWorld = config.getString("lobby.world", "world");
		World world = Bukkit.getWorld(lobbyWorld);
		if (world == null) {
			world = Bukkit.getWorlds().getFirst();
		}
		lobbyLocation = new Location(
				world,
				config.getDouble("lobby.x", world.getSpawnLocation().getX()),
				config.getDouble("lobby.y", world.getSpawnLocation().getY()),
				config.getDouble("lobby.z", world.getSpawnLocation().getZ()),
				(float) config.getDouble("lobby.yaw", 0),
				(float) config.getDouble("lobby.pitch", 0)
		);

		minPlayers = config.getInt("game.min-players", 1);
		maxPlayers = config.getInt("game.max-players", 4);
		startCountdownSeconds = config.getInt("game.start-countdown-seconds", 10);
		betweenWavesSeconds = config.getInt("game.between-waves-seconds", 10);
		victoryWave = config.getInt("game.victory-wave", 30);
		endDelaySeconds = config.getInt("game.end-delay-seconds", 8);
		maxAliveZombies = config.getInt("game.max-alive-zombies", 30);
		downedBleedSeconds = config.getInt("game.downed-bleed-seconds", 30);
		reviveChannelTicks = config.getInt("game.revive-channel-ticks", 40);
		reviveRange = config.getDouble("game.revive-range", 4.0);

		victoryBonus = config.getInt("rewards.victory-bonus", 500);
		perWaveSurvived = config.getInt("rewards.per-wave-survived", 50);
		perKill = config.getInt("rewards.per-kill", 10);
		headshotDamageMultiplier = config.getDouble("combat.headshot-damage-multiplier", 1.5);
		npcJoinOnly = config.getBoolean("join.npc-only", true);
		autoJoinTemplate = config.getString("join.template", "void");
		if (autoJoinTemplate == null || autoJoinTemplate.isBlank()) {
			autoJoinTemplate = "void";
		}

		templateSpawnOffsets.clear();
		ConfigurationSection templates = config.getConfigurationSection("templates");
		if (templates != null) {
			for (String template : templates.getKeys(false)) {
				List<String> raw = templates.getStringList(template + ".spawn-offsets");
				List<Vector> offsets = new ArrayList<>();
				for (String entry : raw) {
					Vector offset = parseOffset(entry);
					if (offset != null) {
						offsets.add(offset);
					}
				}
				templateSpawnOffsets.put(template.toLowerCase(), offsets);
			}
		}
	}

	public void addSpawnOffset(String template, Vector offset) {
		String key = template.toLowerCase();
		templateSpawnOffsets.computeIfAbsent(key, _ -> new ArrayList<>()).add(offset);

		List<String> serialized = templateSpawnOffsets.get(key).stream()
				.map(v -> v.getBlockX() + "," + v.getBlockY() + "," + v.getBlockZ())
				.toList();
		plugin.getConfig().set("templates." + key + ".spawn-offsets", serialized);
		plugin.saveConfig();
	}

	public List<Vector> getSpawnOffsets(String template) {
		return templateSpawnOffsets.getOrDefault(template.toLowerCase(), Collections.emptyList());
	}

	private static Vector parseOffset(String entry) {
		String[] parts = entry.split(",");
		if (parts.length != 3) return null;
		try {
			return new Vector(
					Double.parseDouble(parts[0].trim()),
					Double.parseDouble(parts[1].trim()),
					Double.parseDouble(parts[2].trim())
			);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public Location getLobbyLocation() {
		return lobbyLocation.clone();
	}

	public double getReviveRangeSquared() {
		return reviveRange * reviveRange;
	}

}
