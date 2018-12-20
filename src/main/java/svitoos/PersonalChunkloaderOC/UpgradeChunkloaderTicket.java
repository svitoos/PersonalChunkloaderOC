package svitoos.PersonalChunkloaderOC;

import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;

import net.minecraftforge.common.ForgeChunkManager;

class UpgradeChunkloaderTicket {

  static Map<String, UpgradeChunkloaderTicket> regTickets = new HashMap<>();

  static boolean isValid(ForgeChunkManager.Ticket fcmTicket) {
    NBTTagCompound data = fcmTicket.getModData();
    return data.hasKey("x") && data.hasKey("y") && data.hasKey("z") && data.hasKey("address");
  }

  static boolean ticketAvailableFor(String username) {
    return Math.min(Config.maxTicketsPerPlayer, ForgeChunkManager.ticketCountAvailableFor(username))
        > 0;
  }

  private final ForgeChunkManager.Ticket fcmTicket;
  final String address;
  final String owner;
  final int dimensionId;
  private ChunkCoordinates blockCoord;
  UpgradeChunkloaderEnv loader;

  UpgradeChunkloaderTicket(ForgeChunkManager.Ticket fcmTicket) {
    this.fcmTicket = fcmTicket;
    NBTTagCompound data = fcmTicket.getModData();
    address = data.getString("address");
    owner = fcmTicket.getPlayerName();
    this.dimensionId = fcmTicket.world.provider.dimensionId;
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

  ChunkCoordinates getBlockCoord() {
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
        }
      }
    }
  }

  void forceLoad() {
    fcmTicket.world.getChunkFromBlockCoords(blockCoord.posX, blockCoord.posZ);
  }

  void unforceChunks() {
    for (ChunkCoordIntPair chunkCoord : fcmTicket.getChunkList()) {
      PersonalChunkloaderOC.info("Unforce chunk %s by %s", chunkCoord, address);
      ForgeChunkManager.unforceChunk(fcmTicket, chunkCoord);
    }
  }

  static UpgradeChunkloaderTicket request(
      World world, ChunkCoordinates hostCoord, String playerName, String componentAddress) {
    ForgeChunkManager.Ticket fcmTicket =
        ForgeChunkManager.requestPlayerTicket(
            PersonalChunkloaderOC.instance, playerName, world, ForgeChunkManager.Type.NORMAL);
    if (fcmTicket != null) {
      return new UpgradeChunkloaderTicket(
          fcmTicket, componentAddress, world.provider.dimensionId, hostCoord);
    }
    return null;
  }

  void release() {
    try {
      ForgeChunkManager.releaseTicket(fcmTicket);
    } catch (Throwable e) {
      // Ignored.
    }
  }

  public String toString() {
    final Formatter f = new Formatter();
    f.format(
        "ticket %s/%s at (%d, %d, %d) in dim %d",
        address, owner, blockCoord.posX, blockCoord.posY, blockCoord.posZ, dimensionId);
    return f.toString();
  }
}
