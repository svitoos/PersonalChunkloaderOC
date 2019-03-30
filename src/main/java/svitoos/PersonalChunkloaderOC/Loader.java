package svitoos.PersonalChunkloaderOC;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.SetMultimap;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.PlayerOrderedLoadingCallback;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.ForgeChunkManager.Type;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

class Loader {

  private static SetMultimap<String, Integer> unloadedDims = HashMultimap.create();
  private static Map<String, Loader> loaders = new HashMap<>();
  private static List<Loader> unloadedLoaders = new ArrayList<>();

  final String address;
  final String ownerName;
  final int dimensionId;
  private Ticket ticket;
  private boolean active;
  private ChunkCoordinates blockCoord;
  private ChunkCoordIntPair centerChunk;
  boolean connected;

  private Loader(Ticket ticket, String address, ChunkCoordinates blockCoord) {
    assert !loaders.containsKey(address);
    this.ticket = ticket;
    this.address = address;
    ownerName = ticket.getPlayerName();
    dimensionId = ticket.world.provider.dimensionId;
    active = (getPlayer(ownerName) != null);
    setCoordinates(blockCoord);
    loaders.put(address, this);
  }

  private boolean setCoordinates(ChunkCoordinates blockCoord) {
    if (this.blockCoord != null && this.blockCoord.equals(blockCoord)) {
      return false;
    }
    this.blockCoord = blockCoord;
    NBTTagCompound data = ticket.getModData();
    data.setInteger("x", blockCoord.posX);
    data.setInteger("y", blockCoord.posY);
    data.setInteger("z", blockCoord.posZ);

    ChunkCoordIntPair newCenterChunk =
        new ChunkCoordIntPair(blockCoord.posX >> 4, blockCoord.posZ >> 4);
    if (centerChunk != null && centerChunk.equals(newCenterChunk)) {
      return false;
    }

    centerChunk = newCenterChunk;
    return true;
  }

  void delete() {
    if (loaders.remove(address) != null) {
      ForgeChunkManager.releaseTicket(ticket);
      ticket = null;
      if (Config.chunkloaderLogLevel >= 2) {
        PersonalChunkloaderOC.info("Removed: %s", this);
      }
    }
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

  void update(ChunkCoordinates blockCoord) {
    if (setCoordinates(blockCoord) && active) {
      updateChunks();
    }
  }

  private void updateChunks() {

    for (ChunkCoordIntPair chunkCoord : ticket.getChunkList()) {
      if (!(chunkCoord.chunkXPos >= centerChunk.chunkXPos - 1
          && chunkCoord.chunkXPos <= centerChunk.chunkXPos + 1
          && chunkCoord.chunkZPos >= centerChunk.chunkZPos - 1
          && chunkCoord.chunkZPos <= centerChunk.chunkZPos + 1)) {
        unforceChunk(chunkCoord);
      }
    }
    for (int x = -1; x < 2; x++) {
      for (int z = -1; z < 2; z++) {
        ChunkCoordIntPair chunkCoord =
            new ChunkCoordIntPair(centerChunk.chunkXPos + x, centerChunk.chunkZPos + z);
        if (!ticket.getChunkList().contains(chunkCoord)) {
          forceChunk(chunkCoord);
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

  private void force() {
    ticket.world.getChunkFromChunkCoords(centerChunk.chunkXPos, centerChunk.chunkZPos);
  }

  private void forceChunk(ChunkCoordIntPair chunkCoord) {
    if (Config.chunkloaderLogLevel >= 3) {
      PersonalChunkloaderOC.info("Force chunk %s by %s", chunkCoord, address);
    }
    ForgeChunkManager.forceChunk(ticket, chunkCoord);
  }

  private void unforceChunk(ChunkCoordIntPair chunkCoord) {
    if (Config.chunkloaderLogLevel >= 3) {
      PersonalChunkloaderOC.info("Unforce chunk %s by %s", chunkCoord, address);
    }
    ForgeChunkManager.unforceChunk(ticket, chunkCoord);
  }

  @Override
  public String toString() {
    final Formatter f = new Formatter();
    f.format(
        "chunkloader by %s/%s at (%d, %d, %d) in dim %d",
        address,
        ownerName,
        blockCoord.posX,
        blockCoord.posY,
        blockCoord.posZ,
        dimensionId);

    return f.toString();
  }

  static Loader create(String address, String ownerName, World world, ChunkCoordinates blockCoord) {
    if (loaders.containsKey(address) || !allowed(ownerName, world, blockCoord)) {
      return null;
    }
    Ticket ticket =
        ForgeChunkManager.requestPlayerTicket(
            PersonalChunkloaderOC.instance, ownerName, world, Type.NORMAL);
    if (ticket == null) {
      return null;
    }
    Loader loader = new Loader(ticket, address, blockCoord);
    ticket.getModData().setString("address", address);
    loader.connected = true;
    if (Config.chunkloaderLogLevel >= 2) {
      PersonalChunkloaderOC.info("Added: %s", loader);
    }
    if (loader.active) {
      loader.updateChunks();
    }
    return loader;
  }

  static Loader restore(
      String address, String ownerName, World world, ChunkCoordinates blockCoord) {
    Loader loader = loaders.get(address);
    if (loader != null && !loader.connected) {
      if (loader.ownerName.equals(ownerName)
          && loader.ticket.world == world
          && allowed(ownerName, world, blockCoord)) {
        loader.connected = true;
        loader.setCoordinates(blockCoord);
        if (Config.chunkloaderLogLevel >= 2) {
          PersonalChunkloaderOC.info("Restored: %s", loader);
        }
        if (loader.active) {
          loader.updateChunks();
        }
        return loader;
      }
      loader.delete();
    }
    return null;
  }

  private static boolean allowed(String ownerName, World world, ChunkCoordinates blockCoord) {
    if (ownerName == null) {
      return false;
    }

    if (Math.min(Config.maxTicketsPerPlayer, ForgeChunkManager.ticketCountAvailableFor(ownerName))
        < 1) {
      return false;
    }

    if (getPlayer(ownerName) instanceof FakePlayer) {
      return false;
    }

    final int dimensionId = world.provider.dimensionId;

    if (!allowedDim(dimensionId)) {
      return false;
    }

    if (!allowedCoord(dimensionId, blockCoord)) {
      return false;
    }

    return true;
  }

  private static boolean allowedDim(int dimensionId) {
    return true;
  }

  private static boolean allowedCoord(int dimensionId, ChunkCoordinates blockCoord) {
    return true;
  }

  private static EntityPlayerMP getPlayer(String playerName) {
    return MinecraftServer.getServer().getConfigurationManager().func_152612_a(playerName);
  }

  public static class Handler implements PlayerOrderedLoadingCallback {
    @Override
    public ListMultimap<String, Ticket> playerTicketsLoaded(
        ListMultimap<String, Ticket> tickets, World world) {

      ListMultimap<String, Ticket> loaded = ArrayListMultimap.create();

      ListMultimap<String, Ticket> valid = ArrayListMultimap.create();

      if (allowedDim(world.provider.dimensionId)) {
        tickets
            .keySet()
            .forEach(
                playerName -> {
                  List<Ticket> playerTickets = tickets.get(playerName);
                  final int ticketCountAvailable =
                      ForgeChunkManager.ticketCountAvailableFor(playerName);
                  int ticketCount = 0;
                  for (Ticket ticket : playerTickets) {
                    if (validateTicket(ticket)) {
                      ticketCount++;
                      if (ticketCount > ticketCountAvailable) {
                        break;
                      }
                      loaded.put(ticket.getPlayerName(), ticket);
                    }
                  }
                });
      }
      return loaded;
    }

    private static boolean validateTicket(Ticket ticket) {
      NBTTagCompound data = ticket.getModData();
      if (!(data.hasKey("x")
          && data.hasKey("y")
          && data.hasKey("z")
          && data.hasKey("address")
          && !data.getString("address").isEmpty())) {
        return false;
      }
      return allowed(
          ticket.getPlayerName(),
          ticket.world,
          new ChunkCoordinates(data.getInteger("x"), data.getInteger("y"), data.getInteger("z")));
    }

    @Override
    public void ticketsLoaded(List<Ticket> tickets, World world) {
      tickets.forEach(
          ticket -> {
            NBTTagCompound data = ticket.getModData();
            String address = data.getString("address");
            if (loaders.containsKey(address)) {
              PersonalChunkloaderOC.warn("Remove duplicate ticket %s", address);
              ForgeChunkManager.releaseTicket(ticket);
            } else {
              Loader loader =
                  new Loader(
                      ticket,
                      address,
                      new ChunkCoordinates(
                          data.getInteger("x"), data.getInteger("y"), data.getInteger("z")));
              if (Config.chunkloaderLogLevel >= 1) {
                PersonalChunkloaderOC.info("Loaded: %s", loader);
              }
            }
          });
    }

    @SuppressWarnings("unused")
    @SubscribeEvent(priority = EventPriority.HIGH) // after ForgeChunkManager
    public void onWorldLoad(WorldEvent.Load e) {
      int dimensionId = e.world.provider.dimensionId;
      unloadedDims.entries().removeIf(entry -> entry.getValue() == dimensionId);
      unloadedLoaders.removeIf(loader -> loader.dimensionId == dimensionId);
      ImmutableList.copyOf(
              loaders
                  .values()
                  .stream()
                  .filter(loader -> loader.dimensionId == dimensionId && loader.active)
                  .iterator())
          .forEach(Loader::force);
    }

    @SuppressWarnings("unused")
    @SubscribeEvent(
        priority =
            EventPriority
                .HIGH) // after ForgeChunkManager, before UpgradeChunkloaderEnv.onDisconnect
    public void onWorldUnload(WorldEvent.Unload e) {
      int dimensionId = e.world.provider.dimensionId;
      ImmutableList.copyOf(
              loaders
                  .values()
                  .stream()
                  .filter(loader -> loader.dimensionId == dimensionId)
                  .iterator())
          .forEach(
              loader -> {
                if (Config.chunkloaderLogLevel >= 1) {
                  PersonalChunkloaderOC.info("Stored: %s", loader);
                }
                unloadedDims.put(loader.ownerName, dimensionId);
                loaders.remove(loader.address);
                unloadedLoaders.add(loader);
              });
    }

    @SuppressWarnings("unused")
    @SubscribeEvent(priority = EventPriority.LOWEST) // after UpgradeChunkloaderEnv.onConnect
    public void onChunkLoad(ChunkEvent.Load e) {
      int dimensionId = e.getChunk().worldObj.provider.dimensionId;
      ChunkCoordIntPair chunkCoord = e.getChunk().getChunkCoordIntPair();
      ImmutableList.copyOf(
              loaders
                  .values()
                  .stream()
                  .filter(
                      loader ->
                          loader.dimensionId == dimensionId
                              && chunkCoord.equals(loader.centerChunk)
                              && !loader.connected)
                  .iterator())
          .forEach(
              loader -> {
                PersonalChunkloaderOC.warn("Invalid: %s", loader);
                loader.delete();
              });
    }

    @SuppressWarnings("unused")
    @SubscribeEvent(priority = EventPriority.HIGHEST) // before UpgradeChunkloaderEnv.onDisconnect
    public void onChunkUnload(ChunkEvent.Unload e) {
      int dimensionId = e.getChunk().worldObj.provider.dimensionId;
      ChunkCoordIntPair chunkCoord = e.getChunk().getChunkCoordIntPair();
      ImmutableList.copyOf(
              loaders
                  .values()
                  .stream()
                  .filter(
                      loader ->
                          loader.dimensionId == dimensionId
                              && chunkCoord.equals(loader.centerChunk))
                  .iterator())
          .forEach(loader -> loader.connected = false);
    }

    @SuppressWarnings("unused")
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent e) {
      String playerName = e.player.getCommandSenderName();

      ImmutableList.copyOf(
              loaders
                  .values()
                  .stream()
                  .filter(loader -> playerName.equals(loader.ownerName))
                  .iterator())
          .forEach(
              loader -> {
                loader.active = true;
                if (!loader.connected) {
                  loader.force(); // force chunk for unloaded chunkloaders
                } else {
                  loader.updateChunks();
                }
              });

      // load unloaded dimensions
      ImmutableSet.copyOf(Loader.unloadedDims.get(playerName))
          .forEach(
              dimensionId -> {
                final World world =
                    MinecraftServer.getServer().worldServerForDimension(dimensionId);
                if (world == null) {
                  PersonalChunkloaderOC.warn("Could not load dimension %d", dimensionId);
                }
              });
    }

    @SuppressWarnings("unused")
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent e) {
      String playerName = e.player.getCommandSenderName();

      ImmutableList.copyOf(
              loaders
                  .values()
                  .stream()
                  .filter(loader -> playerName.equals(loader.ownerName))
                  .iterator())
          .forEach(
              loader -> {
                if (loader.active) {
                  loader.active = false;
                  loader.unforceChunks();
                }
              });
    }
  }
}
