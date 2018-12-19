package svitoos.PersonalChunkloaderOC;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;

import net.minecraftforge.common.ForgeChunkManager;

class UpgradeChunkloaderTicket {

  static boolean isValid(ForgeChunkManager.Ticket fcmTicket) {
    NBTTagCompound data = fcmTicket.getModData();
    return data.hasKey("x") && data.hasKey("y") && data.hasKey("z") && data.hasKey("address");
  }

  private final ForgeChunkManager.Ticket fcmTicket;
  final String address;
  final String owner;
  final int dimensionId;
  private ChunkCoordinates blockCoord;
  private boolean checked;

  UpgradeChunkloaderTicket(ForgeChunkManager.Ticket fcmTicket, int dimensionId) {
    this.fcmTicket = fcmTicket;
    NBTTagCompound data = fcmTicket.getModData();
    address = data.getString("address");
    owner = fcmTicket.getPlayerName();
    this.dimensionId = dimensionId;
    blockCoord =
        new ChunkCoordinates(data.getInteger("x"), data.getInteger("y"), data.getInteger("z"));
  }

  private UpgradeChunkloaderTicket(
      ForgeChunkManager.Ticket fcmTicket,
      String address,
      int dimensionId,
      ChunkCoordinates hostCoord) {
    this.fcmTicket = fcmTicket;
    NBTTagCompound data = fcmTicket.getModData();
    this.address = address;
    data.setString("address", address);
    owner = fcmTicket.getPlayerName();
    this.dimensionId = dimensionId;
    setBlockCoord(hostCoord);
  }

  public ChunkCoordinates getBlockCoord() {
    return blockCoord;
  }

  ChunkCoordIntPair getChunkCoord() {
    return new ChunkCoordIntPair(blockCoord.posX >> 4, blockCoord.posZ >> 4);
  }

  void setBlockCoord(ChunkCoordinates hostCoord) {
    blockCoord = hostCoord;
    NBTTagCompound data = fcmTicket.getModData();
    data.setInteger("x", blockCoord.posX);
    data.setInteger("y", blockCoord.posY);
    data.setInteger("z", blockCoord.posZ);
  }

  void forceChunks() {
    ChunkCoordIntPair centerChunk = getChunkCoord();

    for (ChunkCoordIntPair chunkCoord : fcmTicket.getChunkList()) {
      if (!(chunkCoord.chunkXPos >= centerChunk.chunkXPos - 1
          && chunkCoord.chunkXPos <= centerChunk.chunkXPos + 1
          && chunkCoord.chunkZPos >= centerChunk.chunkZPos - 1
          && chunkCoord.chunkZPos <= centerChunk.chunkZPos + 1)) {
        PersonalChunkloaderOC.info("Unforce chunk %s by %s", chunkCoord, address);
        ForgeChunkManager.unforceChunk(fcmTicket, chunkCoord);
      }
    }
    for (int x = -1; x < 2; x++) {
      for (int z = -1; z < 2; z++) {
        ChunkCoordIntPair chunkCoord =
            new ChunkCoordIntPair(centerChunk.chunkXPos + x, centerChunk.chunkZPos + z);
        if (!fcmTicket.getChunkList().contains(chunkCoord)) {
          PersonalChunkloaderOC.info("Force chunk %s by %s", chunkCoord, address);
          ForgeChunkManager.forceChunk(fcmTicket, chunkCoord);
        } else {
          PersonalChunkloaderOC.info("Alredy Forced chunk %s by %s", chunkCoord, address);
        }
      }
    }
  }

  void forceCenterChunk() {
    PersonalChunkloaderOC.info("Force chunk %s by %s", getChunkCoord(), address);
    ForgeChunkManager.forceChunk(fcmTicket, getChunkCoord());
  }

  void unforceChunks() {
    for (ChunkCoordIntPair chunkCoord : fcmTicket.getChunkList()) {
      PersonalChunkloaderOC.info("Unforce chunk %s by %s", chunkCoord, address);
      ForgeChunkManager.unforceChunk(fcmTicket, chunkCoord);
    }
  }

  static UpgradeChunkloaderTicket request(
      World world, ChunkCoordinates hostCoord, String playerName, String componentAddress) {
    if (ForgeChunkManager.ticketCountAvailableFor(playerName) > 0
        && UpgradeChunkloaderEnv.allowedDim(world.provider.dimensionId)
        && UpgradeChunkloaderEnv.allowedCoord(hostCoord)) {
      ForgeChunkManager.Ticket fcmTicket =
          ForgeChunkManager.requestPlayerTicket(
              PersonalChunkloaderOC.instance, playerName, world, ForgeChunkManager.Type.NORMAL);
      if (fcmTicket != null) {
        PersonalChunkloaderOC.info(
            "Ticket request is successful: player %s= , address = %s",
            playerName, componentAddress); // TODO: level debug
        return new UpgradeChunkloaderTicket(
            fcmTicket, componentAddress, world.provider.dimensionId, hostCoord);
      }
    }
    PersonalChunkloaderOC.info(
        "Ticket request was rejected: player %s= , address = %s",
        playerName, componentAddress); // TODO: level debug
    return null;
  }

  void release() {
    ForgeChunkManager.releaseTicket(fcmTicket);
  }

  boolean unchecked() {
    return !checked;
  }

  void markChecked() {
    checked = true;
  }
}
