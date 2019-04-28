package svitoos.PersonalChunkloaderOC;

import java.io.File;

import java.util.Arrays;
import net.minecraftforge.common.config.Configuration;

class Config {
  private static Configuration configuration;

  static boolean chunkloaderUpgradeRecipe;
  static boolean logRejectedReason;
  static int chunkloaderLogLevel;
  static int maxLoadersPerPlayer;
  static int tickFrequency;
  static boolean disableDrones;
  static boolean disable;
  static int[] dimensionWhitelist;
  static int[] dimensionBlacklist;
  static void init(File file) {
    configuration = new Configuration(file);
    configuration.load();
    chunkloaderUpgradeRecipe =
        configuration.getBoolean(
            "chunkloaderUpgrade", "recipe", true, "Register Chunkloader Upgrade Recipe");
    logRejectedReason = configuration.getBoolean("logRejectedReason", "logging", false, "");
    chunkloaderLogLevel = configuration.getInt("chunkloaderLogLevel", "logging", 1, 0, 5, "");
    maxLoadersPerPlayer =
        configuration.getInt("maxLoadersPerPlayer", "general", 3, 0, Integer.MAX_VALUE, "");
    tickFrequency = configuration.getInt("tickFrequency", "general", 10, 1, Integer.MAX_VALUE, "");
    disableDrones = configuration.getBoolean("disableDrones","general", false, "");
    disable = configuration.getBoolean("disable","general", false, "");
    dimensionWhitelist = configuration.get("general", "dimensionWhitelist", new int[]{}, "").getIntList();
    dimensionBlacklist = configuration.get("general", "dimensionBlacklist", new int[]{}, "").getIntList();
    Arrays.sort(dimensionWhitelist);
    Arrays.sort(dimensionBlacklist);
    configuration.save();
  }
}
