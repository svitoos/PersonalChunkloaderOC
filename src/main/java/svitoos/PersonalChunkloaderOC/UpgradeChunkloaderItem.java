package svitoos.PersonalChunkloaderOC;

import li.cil.oc.api.CreativeTab;

import net.minecraft.item.Item;

public class UpgradeChunkloaderItem extends Item {

  public UpgradeChunkloaderItem() {
    setCreativeTab(CreativeTab.instance);
    setUnlocalizedName("ChunkloaderUpgrade");
    setTextureName(PersonalChunkloaderOC.MOD_ID + ":" + "UpgradeChunkloader");
  }
}
