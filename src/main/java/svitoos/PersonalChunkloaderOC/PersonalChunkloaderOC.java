package svitoos.PersonalChunkloaderOC;

import org.apache.logging.log4j.Level;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(
    modid = PersonalChunkloaderOC.MOD_ID,
    name = PersonalChunkloaderOC.MOD_NAME,
    version = PersonalChunkloaderOC.VERSION /*@MCVERSIONDEP@*/)
public class PersonalChunkloaderOC {

  static final String MOD_ID = "@MODID@";
  static final String MOD_NAME = "@MODNAME@";
  static final String VERSION = "@MODVERSION@";

  @Mod.Instance public static PersonalChunkloaderOC instance;

  @SidedProxy(
      clientSide = "svitoos.PersonalChunkloaderOC.ClientProxy",
      serverSide = "svitoos.PersonalChunkloaderOC.CommonProxy")
  public static CommonProxy proxy;

  static UpgradeChunkloaderItem itemChunkloaderUpgrade;

  @Mod.EventHandler
  public void preInit(FMLPreInitializationEvent e) {
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

  static void info(String format, Object... data) {
    FMLLog.log(MOD_ID, Level.INFO, format, data);
  }

  static void warn(String format, Object... data) {
    FMLLog.log(MOD_ID, Level.WARN, format, data);
  }
}
