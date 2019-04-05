package svitoos.PersonalChunkloaderOC;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

class Config {
  private static Configuration configuration;

  static boolean chunkloaderUpgradeRecipe = true;
  static boolean logRejectedReason = true;
  static int chunkloaderLogLevel = 99;
  static int maxTicketsPerPlayer = 500;
  static int tickFrequency = 10;

  static void init(File file) {
    configuration = new Configuration(file);
    configuration.load();
    chunkloaderUpgradeRecipe =
        configuration.getBoolean(
            "chunkloaderUpgrade", "recipe", true, "Register Chunkloader Upgrade Recipe");
    logRejectedReason = configuration.getBoolean("logRejectedReason", "logging", false, "");
    chunkloaderLogLevel = configuration.getInt("chunkloaderLogLevel", "logging", 1, 0, 5, "");
    maxTicketsPerPlayer =
        configuration.getInt("maxTicketsPerPlayer", "general", 3, 0, Integer.MAX_VALUE, "");
    tickFrequency = configuration.getInt("tickFrequency", "general", 10, 1, Integer.MAX_VALUE, "");
    configuration.save();
  }
}
