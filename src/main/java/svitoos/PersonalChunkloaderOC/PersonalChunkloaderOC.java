package svitoos.PersonalChunkloaderOC;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerAboutToStartEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;

@Mod(
    modid = PersonalChunkloaderOC.MOD_ID,
    name = PersonalChunkloaderOC.MOD_NAME,
    version = PersonalChunkloaderOC.VERSION /*@MCVERSIONDEP@*/)
public class PersonalChunkloaderOC {

  static final String MOD_ID = "@MODID@";
  static final String MOD_NAME = "@MODNAME@";
  static final String VERSION = "@MODVERSION@";

  @Mod.Instance public static PersonalChunkloaderOC instance;

  static Logger logger;

  @SidedProxy(
      clientSide = "svitoos.PersonalChunkloaderOC.ClientProxy",
      serverSide = "svitoos.PersonalChunkloaderOC.CommonProxy")
  public static CommonProxy proxy;

  static UpgradeChunkloaderItem itemChunkloaderUpgrade;

  @Mod.EventHandler
  public void preInit(FMLPreInitializationEvent e) {
    logger = e.getModLog();
    Config.init(e.getSuggestedConfigurationFile());
    proxy.preInit(e);
  }

  @Mod.EventHandler
  public void init(FMLInitializationEvent e) {
    proxy.init(e);
  }

  @Mod.EventHandler
  public void postInit(FMLPostInitializationEvent e) {
    proxy.postInit(e);
  }

  @Mod.EventHandler
  public void serverLoad(FMLServerStartingEvent event) {
    // register server commands
    event.registerServerCommand(new Command());
  }

  @Mod.EventHandler
  public void serverStart(FMLServerAboutToStartEvent event) {
    Loader.init();
    UpgradeChunkloaderEnv.init();
  }

  @Mod.EventHandler
  public void serverStop(FMLServerStoppedEvent event) {
    Loader.cleanup();
    UpgradeChunkloaderEnv.cleanup();
  }

  static void info(String format, Object... data) {
    logger.log(Level.INFO, format, data);
  }

  static void warn(String format, Object... data) {
    logger.log(Level.WARN, format, data);
  }
}
