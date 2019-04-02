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

public class Loader {

  private static Map<String, Loader> loaders;
  private static SetMultimap<String, Integer> unloadedDims;

  final String address;
  final String ownerName;
  final int dimensionId;
  private Ticket ticket;
  private boolean active;
  private ChunkCoordinates blockCoord;
  private ChunkCoordIntPair centerChunk;
  private State state = State.Pending;

  static void init() {
    loaders = new HashMap<>();
    unloadedDims = HashMultimap.create();
  }

  static void cleanup() {
    loaders.clear();
    loaders = null;
    unloadedDims.clear();
    unloadedDims = null;
  }

  public enum State {
    Connected,
    Pending,
    Unloaded
  }

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

  public void delete() {
    if (loaders.remove(address) != null) {
      if (Config.chunkloaderLogLevel >= 2) {
        PersonalChunkloaderOC.info("Removed: %s", this);
      }
      ForgeChunkManager.releaseTicket(ticket);
      ticket = null;
      state = State.Unloaded;
    }
  }

  public boolean restore(String ownerName, World world, ChunkCoordinates blockCoord) {
    assert state == State.Pending;
    if (this.ownerName.equals(ownerName)
        && ticket.world == world
        && allowed(ownerName, world, blockCoord)) {
      state = State.Connected;
      setCoordinates(blockCoord);
      if (Config.chunkloaderLogLevel >= 3) {
        PersonalChunkloaderOC.info("Restored: %s", this);
      }
      if (active) {
        updateChunks();
      }
      return true;
    }
    PersonalChunkloaderOC.warn("Invalid (restore): %s", this);
    delete();
    return false;
  }

  public boolean isConnected() {
    return state == State.Connected;
  }

  public boolean isActive() {
    return active;
  }

  public State getState() {
    return state;
  }

  public ChunkCoordinates getBlockCoord() {
    return blockCoord;
  }

  public ChunkCoordIntPair getChunkCoord() {
    return centerChunk;
  }

  public boolean update(ChunkCoordinates blockCoord) {
    if (allowedCoord(dimensionId, blockCoord)) {
      if (setCoordinates(blockCoord) && active) {
        updateChunks();
      }
      return true;
    } else {
      PersonalChunkloaderOC.warn("Invalid (update): %s", this);
      delete();
      return false;
    }
  }

  public void activate() {
    if (!active) {
      if (Config.chunkloaderLogLevel >= 3) {
        PersonalChunkloaderOC.info("Activate: %s", this);
      }
      active = true;
      if (state == State.Connected) {
        updateChunks(); // включаем подгрузку чанков loader'ом
      } else if (state == State.Pending) {
        force(); // подгружаем выгруженный чанк с loader'ом
      }
    }
  }

  public void deactivate() {
    if (active) {
      if (Config.chunkloaderLogLevel >= 3) {
        PersonalChunkloaderOC.info("Deactivate: %s", this);
      }
      active = false;
      unforceChunks();
    }
  }

  private void unload() {
    state = State.Unloaded;
    ticket = null;
    unloadedDims.put(ownerName, dimensionId);
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
      unforceChunk(chunkCoord);
    }
  }

  private void force() {
    if (Config.chunkloaderLogLevel >= 4) {
      PersonalChunkloaderOC.info("Load center chunk %s : %s", centerChunk, this);
    }
    ticket.world.getChunkFromChunkCoords(centerChunk.chunkXPos, centerChunk.chunkZPos);
  }

  private void forceChunk(ChunkCoordIntPair chunkCoord) {
    if (Config.chunkloaderLogLevel >= 4) {
      PersonalChunkloaderOC.info("Force chunk %s : %s", chunkCoord, this);
    }
    ForgeChunkManager.forceChunk(ticket, chunkCoord);
  }

  private void unforceChunk(ChunkCoordIntPair chunkCoord) {
    if (Config.chunkloaderLogLevel >= 4) {
      PersonalChunkloaderOC.info("Unforce chunk %s : %s", chunkCoord, this);
    }
    ForgeChunkManager.unforceChunk(ticket, chunkCoord);
  }

  @Override
  public String toString() {
    final Formatter f = new Formatter();
    f.format(
        "chunkloader by %s/%s at (%d, %d, %d) in dim %d | state = %s, active = %s",
        address,
        ownerName,
        blockCoord.posX,
        blockCoord.posY,
        blockCoord.posZ,
        dimensionId,
        state,
        active);

    return f.toString();
  }

  public static List<Loader> getLoaders() {
    return ImmutableList.copyOf(loaders.values());
  }

  public static Loader getPendingLoader(String address) {
    Loader loader = loaders.get(address);
    if (loader != null && loader.state == State.Pending) {
      return loader;
    }
    return null;
  }

  public static Loader get(String address) {
    return loaders.get(address);
  }

  public static Loader create(
      String address, String ownerName, World world, ChunkCoordinates blockCoord) {
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
    loader.state = State.Connected;
    if (Config.chunkloaderLogLevel >= 2) {
      PersonalChunkloaderOC.info("Added: %s", loader);
    }
    if (loader.active) {
      loader.updateChunks();
    }
    return loader;
  }

  public static boolean allowed(String ownerName, World world, ChunkCoordinates blockCoord) {
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

  public static boolean allowedDim(int dimensionId) {
    return true;
  }

  public static boolean allowedCoord(int dimensionId, ChunkCoordinates blockCoord) {
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
      int dimensionId = world.provider.dimensionId;
      // удаление старых записей о loader'ах в этом измерении
      ImmutableList.copyOf(
              loaders
                  .values()
                  .stream()
                  .filter(loader -> loader.dimensionId == dimensionId)
                  .iterator())
          .forEach(loader -> loaders.remove(loader.address));
      // создание loader'ов
      for (Ticket ticket : tickets) {
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
      }
    }

    @SubscribeEvent(priority = EventPriority.HIGH) // after ForgeChunkManager
    public void onWorldLoad(WorldEvent.Load e) {
      if (e.world.isRemote) {
        return;
      }
      if (Config.chunkloaderLogLevel >= 4) {
        PersonalChunkloaderOC.info("onWorldLoad");
      }
      int dimensionId = e.world.provider.dimensionId;
      // подгружаем чанки с активными loader'ами
      for (Loader loader : getLoaders()) {
        if (loader.dimensionId == dimensionId && loader.active) {
          loader.force();
        }
      }
    }

    @SubscribeEvent(
        priority =
            EventPriority
                .HIGH) // after ForgeChunkManager, before UpgradeChunkloaderEnv.onDisconnect
    public void onWorldUnload(WorldEvent.Unload e) {
      if (e.world.isRemote) {
        return;
      }
      if (Config.chunkloaderLogLevel >= 4) {
        PersonalChunkloaderOC.info("onWorldUnload");
      }
      int dimensionId = e.world.provider.dimensionId;
      // помечаем loader'ы как выгруженные, но не удаляем их
      for (Loader loader : getLoaders()) {
        if (loader.dimensionId == dimensionId) {
          if (Config.chunkloaderLogLevel >= 1) {
            PersonalChunkloaderOC.info("Stored: %s", loader);
          }
          loader.unload();
        }
      }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) // after UpgradeChunkloaderEnv.onConnect
    public void onChunkLoad(ChunkEvent.Load e) {
      if (e.world.isRemote) {
        return;
      }
      int dimensionId = e.getChunk().worldObj.provider.dimensionId;
      ChunkCoordIntPair chunkCoord = e.getChunk().getChunkCoordIntPair();
      // удаляем "осиротевшие" loader'ы из этого чанка (невосстановленные в методе
      // UpgradeChunkloaderEnv.onConnect)
      for (Loader loader : getLoaders()) {
        if (loader.dimensionId == dimensionId
            && chunkCoord.equals(loader.centerChunk)
            && !loader.isConnected()) {
          PersonalChunkloaderOC.warn("Invalid (orphaned): %s", loader);
          loader.delete();
        }
      }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST) // before UpgradeChunkloaderEnv.onDisconnect
    public void onChunkUnload(ChunkEvent.Unload e) {
      if (e.world.isRemote) {
        return;
      }
      int dimensionId = e.getChunk().worldObj.provider.dimensionId;
      ChunkCoordIntPair chunkCoord = e.getChunk().getChunkCoordIntPair();
      // меняем статус loader'ов в чанке на Pending
      // для предотвращения их удаления в методе UpgradeChunkloaderEnv.onDisconnect
      for (Loader loader : getLoaders()) {
        if (loader.dimensionId == dimensionId
            && chunkCoord.equals(loader.centerChunk)
            && !loader.isConnected()) {
          loader.state = State.Pending;
        }
      }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent e) {
      String playerName = e.player.getCommandSenderName();
      // активируем все loader'ы игрока в загруженных измерениях
      for (Loader loader : getLoaders()) {
        if (playerName.equals(loader.ownerName)) {
          loader.activate();
        }
      }
      // подгружаем все непрогруженные измерения в которых у игрока есть loader'ы
      for (Integer dimensionId : ImmutableSet.copyOf(unloadedDims.get(playerName))) {
        final World world = MinecraftServer.getServer().worldServerForDimension(dimensionId);
        if (world == null) {
          PersonalChunkloaderOC.warn("Could not load dimension %d", dimensionId);
        }
      }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent e) {
      String playerName = e.player.getCommandSenderName();
      // деактивируем все loader'ы игрока в загруженных измерениях
      for (Loader loader : getLoaders()) {
        if (playerName.equals(loader.ownerName)) {
          loader.deactivate();
        }
      }
    }

    private static List<Loader> getLoaders() {
      return ImmutableList.copyOf(
          loaders.values().stream().filter(loader -> loader.state != State.Unloaded).iterator());
    }
  }
}
