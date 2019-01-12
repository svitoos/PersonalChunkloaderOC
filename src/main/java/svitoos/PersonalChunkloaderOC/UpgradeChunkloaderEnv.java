package svitoos.PersonalChunkloaderOC;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
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
  private boolean isSuspend;

  private static HashSet<UpgradeChunkloaderEnv> chunkloaders = new HashSet<>();
  private static Map<String, UpgradeChunkloaderTicket> regTickets = new HashMap<>();
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
      onRobotMove(this);
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
    if (node == this.node() && chunkloaders.add(this)) {
      if (Config.chunkloaderLogLevel >= 3) {
        PersonalChunkloaderOC.info("Connected: %s", this);
      }
      restoreTicket();
    }
  }

  @Override
  public void onDisconnect(Node node) {
    super.onDisconnect(node);
    if (node == this.node() && chunkloaders.remove(this)) {
      if (Config.chunkloaderLogLevel >= 3) {
        PersonalChunkloaderOC.info("Disconnected: %s", this);
      }
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
    String address = node().address();
    ticket = UpgradeChunkloaderHandler.restoredTickets.remove(address);
    if (ticket == null) {
      ticket = regTickets.get(address);
    } else {
      regTickets.put(address, ticket);
    }
    if (ticket != null) {
      if (Config.chunkloaderLogLevel >= 1) {
        PersonalChunkloaderOC.info("Reclaiming ticket for %s", this);
      }
      // check ticket and loader ownerName
      if (getOwnerName() != null && !getOwnerName().equals(ticket.owner)) {
        PersonalChunkloaderOC.warn(
            "owners do not match: %s : loader owned by %s", ticket, getOwnerName());
        releaseTicket();
      }
      if (!allowed()) {
        releaseTicket();
      }
      if (ticket != null) {
        init();
      }
    }
  }

  private void requestTicket() {
    if (allowed()) {
      ticket =
          UpgradeChunkloaderTicket.request(
              host.world(), getHostCoord(), getOwnerName(), node().address());
      if (ticket != null) {
        regTickets.put(ticket.address, ticket);
        init();
      } else {
        if (Config.chunkloaderLogLevel >= 2) {
          PersonalChunkloaderOC.info("Ticket request failed for %s", this);
        }
      }
    }
  }

  private void init() {
    // duplicated chunkloaders will not work
    chunkloaders.forEach(
        loader -> {
          if (loader.node().address().equals(node().address()) && loader != this) {
            loader.releaseTicket();
          }
        });
    ticket.loader = this;
    isSuspend =
        (MinecraftServer.getServer().getConfigurationManager().func_152612_a(getOwnerName())
            == null);
    if (Config.chunkloaderLogLevel >= 2) {
      PersonalChunkloaderOC.info("Activate %s%s", this, isSuspend ? " (suspend)" : "");
    }
    updateLoadedChunks();
  }

  private void releaseTicket() {
    if (ticket != null) {
      ticket.release();
      regTickets.remove(ticket.address);
      ticket = null;
      if (Config.chunkloaderLogLevel >= 2) {
        PersonalChunkloaderOC.info("Deactivate %s", this);
      }
    }
  }

  private boolean allowed() {

    final String ownerName = getOwnerName();
    if (ownerName == null) {
      if (Config.chunkloaderLogLevel >= 2) {
        PersonalChunkloaderOC.info("Activation denied: %s : %s: ", "no owner", this);
      }
      return false;
    }

    if (!UpgradeChunkloaderTicket.ticketAvailableFor(ownerName)) {
      if (Config.chunkloaderLogLevel >= 2) {
        PersonalChunkloaderOC.info("Activation denied: %s : %s: ", "ticket limit", this);
      }
      return false;
    }

    if ((MinecraftServer.getServer().getConfigurationManager().func_152612_a(ownerName)
        instanceof FakePlayer)) {
      if (Config.chunkloaderLogLevel >= 2) {
        PersonalChunkloaderOC.info("Activation denied: %s : %s: ", "owner is fake player", this);
      }
      return false;
    }

    final int dimensionId = host.world().provider.dimensionId;
    if (!allowedDim(dimensionId)) {
      if (Config.chunkloaderLogLevel >= 2) {
        PersonalChunkloaderOC.info("Activation denied: %s : %s: ", "blacklisted dimension", this);
      }
      return false;
    }

    if (!allowedCoord(getHostCoord())) {
      if (Config.chunkloaderLogLevel >= 2) {
        PersonalChunkloaderOC.info("Activation denied: %s : %s: ", "blacklisted area", this);
      }
      return false;
    }
    return true;
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
    assert ticket != null;
    if (!isSuspend) {
      return;
    }
    isSuspend = false;
    if (Config.chunkloaderLogLevel >= 2) {
      PersonalChunkloaderOC.info("Awake %s", this);
    }
    updateLoadedChunks();
  }

  private void suspend() {
    assert ticket != null;
    if (isSuspend) {
      return;
    }
    isSuspend = true;
    if (Config.chunkloaderLogLevel >= 2) {
      PersonalChunkloaderOC.info("Suspend %s", this);
    }
    updateLoadedChunks();
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
    final Formatter f = new Formatter();
    final String ownerName = getOwnerName();
    f.format(
        "chunkloader %s/%s at (%d, %d, %d) in dim %d",
        this.node().address(),
        ownerName != null ? ownerName : "none",
        (int) host.xPosition(),
        (int) host.yPosition(),
        (int) host.zPosition(),
        host.world().provider.dimensionId);

    return f.toString();
  }

  static void onRobotMove(UpgradeChunkloaderEnv loader) {
    if (loader.ticket != null && !loader.ticket.getBlockCoord().equals(loader.getHostCoord())) {
      loader.updateLoadedChunks();
    }
  }

  static void onChunkUnload(int dimensionId, ChunkCoordIntPair chunkCoord) {
    regTickets
        .values()
        .stream()
        .filter(
            ticket ->
                ticket.loader != null
                    && ticket.dimensionId == dimensionId
                    && ticket.getChunkCoord().equals(chunkCoord))
        .forEach(
            ticket -> {
              if (Config.chunkloaderLogLevel >= 3) {
                PersonalChunkloaderOC.info(" Unloading: %s", ticket.loader);
              }
              ticket.loader.ticket = null; // prevent release ticket
              ticket.loader = null; // detach loader from ticket
            });
  }

  static void onWorldLoad(int dimensionId) {
    unloadedDims.entries().removeIf(e -> e.getValue() == dimensionId);
  }

  static void onWorldUnload(int dimensionId) {
    ImmutableList.copyOf(
            regTickets
                .values()
                .stream()
                .filter(ticket -> ticket.dimensionId == dimensionId)
                .iterator())
        .forEach(
            ticket -> {
              if (ticket.loader != null) {
                ticket.loader.ticket = null; // prevent print "Deactivate" message
              }
              if (Config.chunkloaderLogLevel >= 2) {
                PersonalChunkloaderOC.info("Ticket was stored: %s", ticket);
              }
              unloadedDims.put(ticket.owner, dimensionId);
              regTickets.remove(ticket.address);
            });
  }

  static void onPlayerLoggedIn(String playerName) {
    ImmutableList.copyOf(
            regTickets
                .values()
                .stream()
                .filter(ticket -> playerName.equals(ticket.owner))
                .iterator())
        .forEach(
            ticket -> {
              if (ticket.loader == null) {
                ticket.forceLoad(); // force chunk for unloaded chunkloaders
              } else {
                ticket.loader.awake(); // awake loaded chunkloaders
              }
            });

    // load unloaded dimensions
    ImmutableSet.copyOf(unloadedDims.get(playerName))
        .forEach(
            dimensionId -> {
              final World world = MinecraftServer.getServer().worldServerForDimension(dimensionId);
              if (world == null) {
                PersonalChunkloaderOC.warn("Could not load dimension %d", dimensionId);
              }
            });
  }

  static void onPlayerLoggedOut(String playerName) {
    PersonalChunkloaderOC.info("onPlayerLoggedOut: %s", playerName);

    ImmutableList.copyOf(
            chunkloaders
                .stream()
                .filter(loader -> playerName.equals(loader.getOwnerName()))
                .iterator())
        .forEach(
            loader -> {
              if (loader.ticket != null) {
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
