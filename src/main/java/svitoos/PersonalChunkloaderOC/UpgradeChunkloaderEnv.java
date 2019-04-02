package svitoos.PersonalChunkloaderOC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.util.Formatter;
import java.util.HashMap;

import java.util.Map;
import li.cil.oc.api.Network;
import li.cil.oc.api.event.RobotMoveEvent;
import li.cil.oc.api.internal.Agent;
import li.cil.oc.api.internal.Drone;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.ManagedEnvironment;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChunkCoordinates;

public class UpgradeChunkloaderEnv extends ManagedEnvironment {
  private final EnvironmentHost host;

  private Loader loader;
  private boolean active;
  private final boolean isDrone;
  private boolean connected;

  private static Map<String, UpgradeChunkloaderEnv> activeUpgrades;

  static void init() {
    activeUpgrades = new HashMap<>();
  }

  static void cleanup() {
    activeUpgrades.clear();
    activeUpgrades = null;
  }

  public UpgradeChunkloaderEnv(EnvironmentHost host) {
    this.host = host;
    isDrone = host instanceof Drone;
    setNode(
        Network.newNode(this, Visibility.Network)
            .withComponent("chunkloader")
            .withConnector()
            .create());
  }

  @Override
  public boolean canUpdate() {
    return isDrone;
  }

  @Override
  public void update() {
    super.update();
    if (hasLoader() && host.world().getTotalWorldTime() % Config.tickFrequency == 0) {
      onMove(); // Robot move events are not fired for entities (drones)
    }
  }

  @Callback(doc = "function(enable:boolean):boolean -- Enables or disables the chunkloader.")
  public Object[] setActive(Context context, Arguments arguments) {
    setActive(arguments.checkBoolean(0));
    return new Object[] {active};
  }

  @Callback(doc = "function():boolean -- Gets whether the chunkloader is currently active.")
  public Object[] isActive(Context context, Arguments arguments) {
    return new Object[] {active};
  }

  @Override
  public void load(NBTTagCompound nbt) {
    super.load(nbt);
    if (isDrone) {
      active = nbt.hasKey("active");
    }
  }

  @Override
  public void onConnect(Node node) {
    super.onConnect(node);
    if (node == this.node() && !connected) {
      connected = true; // workaround: предотвращаем повторный вызов onConnect для this.node()
      if (Config.chunkloaderLogLevel >= 4) {
        PersonalChunkloaderOC.info("Connected: %s", this);
      }
      loader = Loader.getPendingLoader(node.address());
      if (loader != null) {
        // temp workaround: onConnect вызывается до чтения имени владельца из nbt
        final String ownerName = isDrone && active ? loader.ownerName : getOwnerName();
        if (!loader.restore(ownerName, host.world(), getHostCoord())) {
          deleteLoader();
        }
      } else if (isDrone && active) {
        active = false;
        UpgradeChunkloaderEnv old = activeUpgrades.get(node.address());
        if (old != null) {
          if (Config.chunkloaderLogLevel >= 3) {
            PersonalChunkloaderOC.info("TravelToDimension: %s", this);
          }
          old.deleteLoader();
          // temp workaround: для дрона onConnect вызывается до чтения имени владельца из nbt
          createLoader(old.getOwnerName());
        }
      }
    }
  }

  @Override
  public void onDisconnect(Node node) {
    super.onDisconnect(node);
    if (node == this.node()) {
      connected = false;
      if (Config.chunkloaderLogLevel >= 4) {
        PersonalChunkloaderOC.info("Disconnected: %s", this);
      }
      if (hasLoader() && loader.isConnected()) {
        deleteLoader();
      }
    }
  }

  @Override
  public void save(NBTTagCompound nbt) {
    super.save(nbt);
    if (isDrone) {
      if (active) {
        nbt.setBoolean("active", true);
      } else if (nbt.hasKey("active")) {
        nbt.removeTag("active");
      }
    }
  }

  @Override
  public void onMessage(Message message) {
    super.onMessage(message);
    if (Config.chunkloaderLogLevel >= 4) {
      PersonalChunkloaderOC.info("onMessage(%s): %s", message.name(), this);
    }
    if (message.name().equals("computer.stopped")) {
      setActive(false);
    } else if (message.name().equals("computer.started")) {
      setActive(true);
    }
  }

  private void setActive(boolean enable) {
    if (enable && loader == null) {
      createLoader();
    } else if (!enable && loader != null) {
      deleteLoader();
    }
  }

  private void createLoader() {
    assert !hasLoader();
    createLoader(getOwnerName());
  }

  private void createLoader(String ownerName) {
    assert !hasLoader();
    if (activeUpgrades.containsKey(node().address())) {
      return;
    }
    loader = Loader.create(node().address(), ownerName, host.world(), getHostCoord());
    if (hasLoader()) {
      activeUpgrades.put(node().address(), this);
    }
    active = hasLoader();
  }

  private void deleteLoader() {
    assert hasLoader();
    loader.delete();
    loader = null;
    activeUpgrades.remove(node().address());
    active = false;
  }

  private ChunkCoordinates getHostCoord() {
    return new ChunkCoordinates(
        (int) host.xPosition(), (int) host.yPosition(), (int) host.zPosition());
  }

  private boolean hasLoader() {
    return loader != null;
  }

  private String getOwnerName() {
    return ((Agent) host).ownerName();
  }

  @Override
  public String toString() {
    final Formatter f = new Formatter();
    final String ownerName = getOwnerName();
    final Node node = this.node();
    f.format(
        "upgrade %s/%s at (%d, %d, %d) in dim %d %s",
        node != null ? this.node().address() : "?",
        ownerName != null ? ownerName : "none",
        (int) host.xPosition(),
        (int) host.yPosition(),
        (int) host.zPosition(),
        host.world().provider.dimensionId,
        active ? "(active)" : "");
    return f.toString();
  }

  private void onMove() {
    if (hasLoader() && loader.isConnected()) {
      if (!loader.update(getHostCoord())) {
        deleteLoader();
      }
    }
  }

  public static class Handler {
    @SubscribeEvent
    public void onRobotMove(RobotMoveEvent.Post e) {
      final Node machineNode = e.agent.machine().node();
      machineNode
          .reachableNodes()
          .forEach(
              node -> {
                if (node.host() instanceof UpgradeChunkloaderEnv) {
                  ((UpgradeChunkloaderEnv) node.host()).onMove();
                }
              });
    }
  }
}
