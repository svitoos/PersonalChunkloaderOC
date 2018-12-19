package svitoos.PersonalChunkloaderOC;

import li.cil.oc.api.API;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;

public class CommonProxy {

  private UpgradeChunkloaderHandler upgradeChunkloaderHandler = new UpgradeChunkloaderHandler();

  public void preInit(FMLPreInitializationEvent e) {}

  public void init(FMLInitializationEvent e) {
    ForgeChunkManager.setForcedChunkLoadingCallback(
        PersonalChunkloaderOC.instance, upgradeChunkloaderHandler);
    MinecraftForge.EVENT_BUS.register(upgradeChunkloaderHandler);
    FMLCommonHandler.instance().bus().register(upgradeChunkloaderHandler);

    PersonalChunkloaderOC.itemChunkloaderUpgrade = new UpgradeChunkloaderItem();
    GameRegistry.registerItem(PersonalChunkloaderOC.itemChunkloaderUpgrade, "chunkloaderUpgrade");
    API.driver.add(new UpgradeChunkloaderDriver());

    if (Config.chunkloaderUpgradeRecipe) {
      GameRegistry.addRecipe(
          new ItemStack(PersonalChunkloaderOC.itemChunkloaderUpgrade),
          "xgx",
          "cic",
          "xpx",
          'g',
          Blocks.glass,
          'x',
          Items.gold_nugget,
          'c',
          li.cil.oc.api.Items.get("chip2").createItemStack(1),
          'i',
          Items.ender_pearl,
          'p',
          li.cil.oc.api.Items.get("printedCircuitBoard").createItemStack(1));
    }
  }

  public void postInit(FMLPostInitializationEvent e) {}
}
