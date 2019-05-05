package svitoos.PersonalChunkloaderOC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.util.Formatter;

import java.util.HashMap;
import java.util.Map;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.DeviceInfo;
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

import net.minecraft.util.ChunkCoordinates;

public class UpgradeChunkloaderEnv extends ManagedEnvironment implements DeviceInfo {
  private final EnvironmentHost host;
  protected final Map<String, String> deviceInfo;

  private Loader loader;
  private Context hostContext;
  private final boolean isDrone;

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
    deviceInfo = new HashMap<>();
    deviceInfo.put(DeviceAttribute.Class, DeviceClass.Generic);
    deviceInfo.put(DeviceAttribute.Description, "Personal world stabilizer");
    deviceInfo.put(DeviceAttribute.Vendor, "Scrag Technologies");
    deviceInfo.put(DeviceAttribute.Product, "Realizer9001-CL-P");
    isDrone = host instanceof Drone;
    setNode(
        Network.newNode(this, Visibility.Network)
            .withComponent("chunkloader")
            .withConnector()
            .create());
  }

  @Override
  public Map<String, String> getDeviceInfo() {
    return deviceInfo;
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
    boolean enable = arguments.checkBoolean(0);
    if (enable && !hasLoader()) {
      try {
        createLoader();
      } catch (Loader.Error e) {
        return new Object[] {false, e.getMessage()};
      }
    } else if (!enable && hasLoader()) {
      deleteLoader();
    }
    return new Object[] {hasLoader()};
  }

  @Callback(doc = "function():boolean -- Gets whether the chunkloader is currently active.")
  public Object[] isActive(Context context, Arguments arguments) {
    return new Object[] {hasLoader()};
  }

  @Override
  public void onConnect(Node node) {
    super.onConnect(node);
    if (hostContext == null
        && node.host() instanceof Context
        && node.canBeReachedFrom(this.node())) {
      hostContext = (Context) node.host();
      if (Config.chunkloaderLogLevel >= 4) {
        PersonalChunkloaderOC.info("Connected: %s", this);
      }
      if (hostContext.isRunning() && !(isDrone && Config.disableDrones)) {
        loader = Loader.getPendingLoader(this.node().address());
        if (loader != null) {
          // temp workaround: onConnect вызывается до чтения имени владельца из nbt
          final String ownerName = isDrone ? loader.ownerName : getOwnerName();
          try {
            loader.restore(ownerName, host.world(), getHostCoord());
          } catch (Loader.Error e) {
            PersonalChunkloaderOC.warn(
                "Restoring failed: %s : %s : %s", e.getMessage(), this, loader);
            deleteLoader();
          }
        } else if (isDrone) {
          UpgradeChunkloaderEnv old = activeUpgrades.get(this.node().address());
          if (old != null) {
            old.deleteLoader();
            // temp workaround: onConnect вызывается до чтения имени владельца из nbt
            final String ownerName = old.getOwnerName();
            try {
              createLoader(ownerName);
            } catch (Loader.Error e) {
              // ignore
            }
          }
        }
      }
    }
  }

  @Override
  public void onDisconnect(Node node) {
    super.onDisconnect(node);
    if (hostContext != null
        && (node == this.node() || node.host() instanceof Context && node.host() == hostContext)) {
      hostContext = null;
      if (Config.chunkloaderLogLevel >= 4) {
        PersonalChunkloaderOC.info("Disconnected: %s", this);
      }
      if (hasLoader() && loader.isConnected()) {
        deleteLoader();
      }
    }
  }

  @Override
  public void onMessage(Message message) {
    super.onMessage(message);
    if (message.name().equals("computer.stopped")) {
      if (Config.chunkloaderLogLevel >= 4) {
        PersonalChunkloaderOC.info("%s: %s", message.name(), this);
      }
      if (hasLoader()) {
        deleteLoader();
      }
    } else if (message.name().equals("computer.started")) {
      if (Config.chunkloaderLogLevel >= 4) {
        PersonalChunkloaderOC.info("%s: %s", message.name(), this);
      }
      if (!hasLoader()) {
        try {
          createLoader();
        } catch (Loader.Error e) {
          // ignore
        }
      }
    }
  }

  private void createLoader() throws Loader.Error {
    assert !hasLoader();
    createLoader(getOwnerName());
  }

  private void createLoader(String ownerName) throws Loader.Error {
    assert !hasLoader();
    if (isDrone && Config.disableDrones) {
      return;
    }
    try {
      loader = Loader.create(node().address(), ownerName, host.world(), getHostCoord());
      if (hasLoader()) {
        activeUpgrades.put(node().address(), this);
      }
    } catch (Loader.Error e) {
      if (Config.logRejectedReason) {
        PersonalChunkloaderOC.info("Creation failed: %s : %s", e.getMessage(), this);
      }
      throw e;
    }
  }

  private void deleteLoader() {
    assert hasLoader();
    loader.delete();
    activeUpgrades.remove(node().address());
    loader = null;
  }

  private ChunkCoordinates getHostCoord() {
    return new ChunkCoordinates(
        (int) Math.floor(host.xPosition()),
        (int) Math.floor(host.yPosition()),
        (int) Math.floor(host.zPosition()));
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
    final boolean isRunning = hostContext != null && hostContext.isRunning();
    final ChunkCoordinates pos = getHostCoord();
    f.format(
        "upgrade %s/%s at (%d, %d, %d) in dim %d | isRunning=%s",
        node != null ? this.node().address() : "?",
        ownerName != null ? ownerName : "none",
        pos.posX,
        pos.posY,
        pos.posZ,
        host.world().provider.dimensionId,
        isRunning);
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
