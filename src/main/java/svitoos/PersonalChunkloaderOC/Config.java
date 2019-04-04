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
    configuration.save();
  }
}
