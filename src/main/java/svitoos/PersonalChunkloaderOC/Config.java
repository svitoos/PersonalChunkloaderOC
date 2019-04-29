package svitoos.PersonalChunkloaderOC;

import java.io.File;

import java.util.Arrays;
import java.util.Map;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.common.config.Property.Type;

class Config {
  private static Configuration configuration;

  static boolean chunkloaderUpgradeRecipe;
  static boolean logRejectedReason;
  static int chunkloaderLogLevel;
  private static int maxLoadersPerPlayer;
  static int tickFrequency;
  static boolean disableDrones;
  static boolean disable;
  static int[] dimensionWhitelist;
  static int[] dimensionBlacklist;
  private static Map<String, Property> maxLoadersPerPlayerOverride;

  static void init(File file) {
    configuration = new Configuration(file, true);
    configuration.load();
    chunkloaderUpgradeRecipe =
        configuration.getBoolean(
            "chunkloaderUpgrade", "recipe", true, "Register Chunkloader Upgrade Recipe");
    logRejectedReason = configuration.getBoolean("logRejectedReason", "logging", false, "");
    chunkloaderLogLevel = configuration.getInt("chunkloaderLogLevel", "logging", 1, 0, 5, "");
    maxLoadersPerPlayer =
        configuration.getInt("maxLoadersPerPlayer", "general", 3, 0, Integer.MAX_VALUE, "");
    tickFrequency = configuration.getInt("tickFrequency", "general", 10, 1, Integer.MAX_VALUE, "");
    disableDrones = configuration.getBoolean("disableDrones", "general", false, "");
    disable = configuration.getBoolean("disable", "general", false, "");
    dimensionWhitelist =
        configuration.get("general", "dimensionWhitelist", new int[] {}, "").getIntList();
    dimensionBlacklist =
        configuration.get("general", "dimensionBlacklist", new int[] {}, "").getIntList();
    Arrays.sort(dimensionWhitelist);
    Arrays.sort(dimensionBlacklist);
    maxLoadersPerPlayerOverride = configuration.getCategory("maxLoadersPerPlayerOverride");
    configuration.setCategoryComment("maxLoadersPerPlayerOverride", "Personal limits. Overrides maxLoadersPerPlayer value. Entry format: I:<player's name>=<limit>");
    configuration.save();
  }

  static void setLoaderLimitForPlayer(String playerName, int value) {
    if (!maxLoadersPerPlayerOverride.containsKey(playerName)) {
      maxLoadersPerPlayerOverride.put(
          playerName, new Property(playerName, Integer.toString(value), Type.INTEGER));
    } else {
      maxLoadersPerPlayerOverride.get(playerName).set(value);
    }
    configuration.save();
  }

  static int getMaxLoadersPerPlayer(String playerName) {
    if (maxLoadersPerPlayerOverride.containsKey(playerName)) {
      return maxLoadersPerPlayerOverride.get(playerName).getInt();
    }
    return maxLoadersPerPlayer;
  }
}
