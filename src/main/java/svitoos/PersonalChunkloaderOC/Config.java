package svitoos.PersonalChunkloaderOC;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

class Config {
  static Configuration configuration;

  static boolean chunkloaderUpgradeRecipe = true;
  static int chunkloaderLogLevel = 99;

  static void init(File file) {
    configuration = new Configuration(file);
    configuration.load();
    chunkloaderUpgradeRecipe =
        configuration.getBoolean(
            "chunkloaderUpgrade", "recipe", true, "Register Chunkloader Upgrade Recipe");
    configuration.save();
  }
}
