package svitoos.PersonalChunkloaderOC;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.ForgeChunkManager.Type;

class Loader {
  final String address;
  private Ticket ticket;
  private boolean active;
  private String ownerName;
  private ChunkCoordinates blockCoord;
  private ChunkCoordIntPair centerChunk;
  private static Map<String, Loader> loaders = new HashMap<>();

  Loader(String address, String ownerName,) {
    this.address = address;
    this.ownerName = ownerName;
    loaders.put(address, this);
  }

  void delete() {
    if (isActive()) {
      releaseTicket();
    }
    loaders.remove(address);
  }

  static Loader create(String address, String ownerName, World world, ChunkCoordinates blockCoord) {
    Loader loader = new Loader(address, ownerName);
    if (loader.requestTicket(world, blockCoord)) {
      return loader;
    }
    return null;
  }

  static Loader get(String address) {
    return loaders.get(address);
  }

  boolean isActive() {
    return active;
  }

  ChunkCoordinates getBlockCoord() {
    return blockCoord;
  }

  ChunkCoordIntPair getChunkCoord() {
    return centerChunk;
  }

  void activate(String ownerName, World world, ChunkCoordinates blockCoord) {

    if (active) {
      if (ownerName == null || !ownerName.equals(this.ownerName)) {
        this.ownerName = ownerName;
        releaseTicket();
        return;
      }
      if (world != ticket.world) {
        releaseTicket();
        requestTicket(world, blockCoord);
      } else {
        update(blockCoord);
      }
    } else {
      this.ownerName = ownerName;
      requestTicket(world, blockCoord);
    }

  }

  void update(String ownerName, World world, ChunkCoordinates blockCoord) {
    if (ownerName == null) {
      return;
    }
    if (!ownerName.equals(this.ownerName)) {
      releaseTicket();
      return;
    }
    if (world != ticket.world) {
      releaseTicket();
      requestTicket(world, blockCoord);
    } else {
      update(blockCoord);
    }
  }

  void update(ChunkCoordinates blockCoord) {
    ChunkCoordIntPair newCenterChunk =
        new ChunkCoordIntPair(blockCoord.posX >> 4, blockCoord.posZ >> 4);
    this.blockCoord = blockCoord;
    if (centerChunk != null && centerChunk.equals(newCenterChunk)) {
      return;
    }
    centerChunk = newCenterChunk;
    updateChunks();
  }

  private void updateChunks() {
    NBTTagCompound data = ticket.getModData();
    data.setInteger("x", centerChunk.chunkXPos);
    data.setInteger("z", centerChunk.chunkZPos);

    for (ChunkCoordIntPair chunkCoord : ticket.getChunkList()) {
      if (!(chunkCoord.chunkXPos >= centerChunk.chunkXPos - 1
          && chunkCoord.chunkXPos <= centerChunk.chunkXPos + 1
          && chunkCoord.chunkZPos >= centerChunk.chunkZPos - 1
          && chunkCoord.chunkZPos <= centerChunk.chunkZPos + 1)) {
        PersonalChunkloaderOC.info("Unforce chunk %s by %s", chunkCoord, address);
        ForgeChunkManager.unforceChunk(ticket, chunkCoord);
      }
    }
    for (int x = -1; x < 2; x++) {
      for (int z = -1; z < 2; z++) {
        ChunkCoordIntPair chunkCoord =
            new ChunkCoordIntPair(centerChunk.chunkXPos + x, centerChunk.chunkZPos + z);
        if (!ticket.getChunkList().contains(chunkCoord)) {
          PersonalChunkloaderOC.info("Force chunk %s by %s", chunkCoord, address);
          ForgeChunkManager.forceChunk(ticket, chunkCoord);
        }
      }
    }
  }

  private void unforceChunks() {
    for (ChunkCoordIntPair chunkCoord : ticket.getChunkList()) {
      PersonalChunkloaderOC.info("Unforce chunk %s by %s", chunkCoord, address);
      ForgeChunkManager.unforceChunk(ticket, chunkCoord);
    }
  }

  private void requestTicket(World world, ChunkCoordinates blockCoord) {
    assert ticket == null;
    this.ownerName = ownerName;
    ticket =
        ForgeChunkManager.requestPlayerTicket(
            PersonalChunkloaderOC.instance, ownerName, world, Type.NORMAL);
    if (ticket != null) {
      ticket.getModData().setString("address", address);
      updateChunks(blockCoord);
    }
  }

  private void releaseTicket() {
    assert ticket != null;
    ForgeChunkManager.releaseTicket(ticket);
    ticket = null;
    if (Config.chunkloaderLogLevel >= 2) {
      PersonalChunkloaderOC.info("Deactivate %s", this);
    }
  }

}
