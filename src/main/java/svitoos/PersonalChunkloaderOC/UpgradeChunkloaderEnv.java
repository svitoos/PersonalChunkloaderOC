package svitoos.PersonalChunkloaderOC;

import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;

import li.cil.oc.api.Network;
import li.cil.oc.api.internal.Agent;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.ManagedEnvironment;

import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;

public class UpgradeChunkloaderEnv extends ManagedEnvironment {
  private EnvironmentHost host;

  private UpgradeChunkloaderTicket ticket = null;
  private boolean isSuspend = true;

  private static HashSet<UpgradeChunkloaderEnv> chunkloaders = new HashSet<>();
  private static Map<String, UpgradeChunkloaderTicket> restoredTickets = new HashMap<>();
  private static SetMultimap<String, Integer> unloadedDims = HashMultimap.create();

  public UpgradeChunkloaderEnv(EnvironmentHost host) {
    this.host = host;
    setNode(
        Network.newNode(this, Visibility.Network)
            .withComponent("chunkloader")
            .withConnector()
            .create());
  }

  @Override
  public boolean canUpdate() {
    return true;
  }

  @Override
  public void update() {
    super.update();
    if (ticket == null) {
      return;
    }
    if (host instanceof Entity) { // Robot move events are not fired for entities (drones)
      updateLoadedChunks();
    }
  }

  @Callback(doc = "function(enable:boolean):boolean -- Enables or disables the chunkloader.")
  public Object[] setActive(Context context, Arguments arguments) throws Exception {
    setActive(arguments.checkBoolean(0));
    return new Object[] {this.ticket != null};
  }

  @Callback(doc = "function():boolean -- Gets whether the chunkloader is currently active.")
  public Object[] isActive(Context context, Arguments arguments) throws Exception {
    return new Object[] {this.ticket != null};
  }

  static int count = 0;

  @Override
  public void onConnect(Node node) {
    super.onConnect(node);
    if (node == this.node()) {
      if (Config.chunkloaderLogLevel > 1) {
        PersonalChunkloaderOC.info("Connected: %s by %s", this, getOwnerName());
      }
      chunkloaders.add(this);
      count++;
      PersonalChunkloaderOC.info("chunkloaders: %d", count);

      restoreTicket();
    }
  }

  @Override
  public void onDisconnect(Node node) {
    super.onDisconnect(node);
    if (node == this.node()) {
      if (Config.chunkloaderLogLevel > 1) {
        PersonalChunkloaderOC.info("Disconnected: %s by %s", this, getOwnerName());
      }
      chunkloaders.remove(this);
      if (host instanceof Entity
          && ticket != null) { // request new ticket when drone travel to dimension
        releaseTicket();
        chunkloaders.forEach(
            loader -> {
              if (loader.node().address().equals(node.address())
                  && loader.getOwnerName().equals(getOwnerName())
                  && loader.ticket == null) {
                loader.requestTicket();
              }
            });
      } else {
        releaseTicket();
      }
    }
  }

  @Override
  public void onMessage(Message message) {
    super.onMessage(message);
    if (message.name().equals("computer.stopped")) {
      setActive(false);
    } else if (message.name().equals("computer.started")) {
      setActive(true);
    }
  }

  private void setActive(boolean enable) {
    if (enable && ticket == null) {
      requestTicket();
    } else if (!enable && ticket != null) {
      releaseTicket();
    }
  }

  private void restoreTicket() {
    if (restoredTickets.containsKey(node().address())) {
      PersonalChunkloaderOC.info("Reclaiming chunk loader ticket for upgrade: %s", this);
      init(restoredTickets.remove(node().address()));
    }
  }

  private void requestTicket() {
    final int dimensionId = host.world().provider.dimensionId;
    if (allowedDim(dimensionId) /*&& node.globalBuffer() < Settings.get.chunkloaderCost*/) {
      final String ownerName = getOwnerName();
      if (ownerName != null) {
        if (!(MinecraftServer.getServer().getConfigurationManager().func_152612_a(ownerName)
            instanceof FakePlayer)) {
          init(
              UpgradeChunkloaderTicket.request(
                  host.world(), getHostCoord(), ownerName, node().address()));
        }
      }
    }
  }

  private void init(UpgradeChunkloaderTicket ticket) {
    this.ticket = ticket;
    if (ticket != null) {
      ticket.markChecked();
      isSuspend =
          MinecraftServer.getServer().getConfigurationManager().func_152612_a(getOwnerName())
              == null;
      PersonalChunkloaderOC.info("isSuspend: %b", isSuspend);
      // duplicated chunkloaders will not work
      chunkloaders.forEach(
          loader -> {
            if (loader.node().address().equals(node().address()) && loader != this) {
              loader.releaseTicket();
            }
          });
      if (Config.chunkloaderLogLevel > 0) {
        PersonalChunkloaderOC.info("Activated: %s", this);
      }
      updateLoadedChunks();
    }
  }

  private void releaseTicket() {
    if (ticket != null) {
      ticket.release();
      ticket = null;
      if (Config.chunkloaderLogLevel > 0) {
        PersonalChunkloaderOC.info("Deactivated: %s", this);
      }
    }
  }

  private void updateLoadedChunks() {
    ticket.setBlockCoord(getHostCoord());
    if (isSuspend) {
      ticket.unforceChunks();
    } else {
      ticket.forceChunks();
    }
  }

  private void awake() {
    PersonalChunkloaderOC.info("awake: isSuspend = %b", isSuspend);
    if (!isSuspend) {
      return;
    }
    isSuspend = false;
    if (Config.chunkloaderLogLevel > 0) {
      PersonalChunkloaderOC.info("Awake: %s", this);
    }
    if (ticket != null) {
      updateLoadedChunks();
    }
  }

  private void suspend() {
    if (isSuspend) {
      return;
    }
    isSuspend = true;
    if (Config.chunkloaderLogLevel > 0) {
      PersonalChunkloaderOC.info("Suspend: %s", this);
    }
    if (ticket != null) {
      updateLoadedChunks();
    }
  }

  private ChunkCoordIntPair getHostChunkCoord() {
    return new ChunkCoordIntPair((int) host.xPosition() >> 4, (int) host.zPosition() >> 4);
  }

  private ChunkCoordinates getHostCoord() {
    return new ChunkCoordinates(
        (int) host.xPosition(), (int) host.yPosition(), (int) host.zPosition());
  }

  private String getOwnerName() {
    return ((Agent) host).ownerName();
  }

  @Override
  public String toString() {
    // 		val sAddress = s"${node.address}"
    // val sActive = if (ticket.isDefined) ", active" else ", inactive"
    // val sSuspend = if (ticket.isDefined && isSuspend) "/suspend" else ""
    // val sOwner =  if (getOwnerName.isDefined) s", owned by ${getOwnerName.get}" else ""
    // val sCoord = s", $hostCoord$hostChunkCoord/${host.world.provider.dimensionId}"
    // s"chunkloader{$sAddress$sActive$sSuspend$sCoord$sOwner}"
    final Formatter f = new Formatter();
    f.format("chunkloader{%s}", node().address());
    return f.toString();
  }

  static void loadTicket(UpgradeChunkloaderTicket ticket) {
    restoredTickets.put(ticket.address, ticket);
    //   OpenComputers.log.info("[chunkloader] Restoring: $ticket")
    if (MinecraftServer.getServer().getConfigurationManager().func_152612_a(ticket.owner) != null) {
      ticket.forceCenterChunk();
    }
  }

  static void onWorldSave() {
    // Any tickets that were not reassigned by the time the world gets saved
    // again can be considered orphaned, so we release them.
    // TODO figure out a better event *after* tile entities were restored
    // but *before* the world is saved, because the tickets are saved first,
    // so if the save is because the game is being quit the tickets aren't
    // actually being cleared. This will *usually* not be a problem, but it
    // has room for improvement.
    for (UpgradeChunkloaderTicket ticket : restoredTickets.values()) {
      if (ticket.unchecked()) {
        PersonalChunkloaderOC.info("Removing orphaned: %s", ticket.address);
        ticket.release();
        restoredTickets.remove(ticket.address);
      }
    }
  }

  static void onRobotMove(UpgradeChunkloaderEnv loader) {
    if (loader.ticket != null) {
      loader.updateLoadedChunks();
    }
  }

  static void onChunkUnload(int dimensionId, ChunkCoordIntPair chunkCoord) {
    chunkloaders.forEach(
        loader -> {
          if (loader.ticket != null
              && loader.ticket.dimensionId == dimensionId
              && loader.ticket.getChunkCoord().equals(chunkCoord)) {
            restoredTickets.put(loader.ticket.address, loader.ticket);
            //     if (Settings.get.chunkloaderLogLevel > 0)
            //       OpenComputers.log.info("[chunkloader] Unloading: $loader")
            loader.ticket = null; // prevent release
          }
        });
  }

  static void onWorldLoad(int dimensionId) {
    unloadedDims.entries().removeIf(e -> e.getValue() == dimensionId);
  }

  static void onWorldUnload(int dimensionId) {
    restoredTickets
        .values()
        .forEach(
            ticket -> {
              if (ticket.dimensionId == dimensionId) {
                //   OpenComputers.log.info("[chunkloader] Unloading: $ticket")
                unloadedDims.put(ticket.owner, dimensionId);
              }
            });
    restoredTickets.entrySet().removeIf(e -> e.getValue().dimensionId == dimensionId);
  }

  static void onPlayerLoggedIn(String playerName) {
    PersonalChunkloaderOC.info("onPlayerLoggedIn: %s", playerName);
    // awake chunk loaders
    chunkloaders.forEach(
        loader -> {
          PersonalChunkloaderOC.info(
              "onPlayerLoggedIn: %s %s %b",
              loader, loader.getOwnerName(), loader.getOwnerName().equals(playerName));
          if (playerName.equals(loader.getOwnerName())) {
            PersonalChunkloaderOC.info("onPlayerLoggedIn: try awake");
            loader.awake();
          }
        });
    // force unloaded tickets
    restoredTickets
        .values()
        .forEach(
            ticket -> {
              PersonalChunkloaderOC.info("onPlayerLoggedIn: ticket %s", ticket.address);
              if (playerName.equals(ticket.owner)) {
                ticket.forceCenterChunk();
              }
            });
    // load unloaded dimensions
    unloadedDims
        .get(playerName)
        .forEach(
            dimensionId -> {
              final World world = MinecraftServer.getServer().worldServerForDimension(dimensionId);
              if (world == null) {
                PersonalChunkloaderOC.warn("Could not load dimension %d", dimensionId);
              }
            });
  }

  static void onPlayerLoggedOut(String playerName) {
    PersonalChunkloaderOC.info("onPlayerLoggedIn: %s", playerName);
    // suspend chunk loaders
    chunkloaders.forEach(
        loader -> {
          if (playerName.equals(loader.getOwnerName())) {
            loader.suspend();
          }
        });
  }

  static boolean allowedDim(int dim) {
    // TODO: implement
    return true;
  }

  static boolean allowedCoord(ChunkCoordinates coord) {
    // TODO: implement
    return true;
  }
}
