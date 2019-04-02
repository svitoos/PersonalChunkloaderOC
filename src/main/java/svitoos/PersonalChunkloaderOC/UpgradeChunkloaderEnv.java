package svitoos.PersonalChunkloaderOC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;

import java.util.Map;
import li.cil.oc.api.Network;
import li.cil.oc.api.event.RobotMoveEvent;
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
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChunkCoordinates;

public class UpgradeChunkloaderEnv extends ManagedEnvironment {
  private final EnvironmentHost host;

  private Loader loader;
  private boolean active;

  private static final HashSet<UpgradeChunkloaderEnv> upgrades = new HashSet<>();
  private static final Map<String, UpgradeChunkloaderEnv> activeUpgrades = new HashMap<>();

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
    return host instanceof Entity;
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
    active = nbt.hasKey("active");
  }

  @Override
  public void onConnect(Node node) {
    super.onConnect(node);
    if (node == this.node() && upgrades.add(this)) {
      if (Config.chunkloaderLogLevel >= 4) {
        PersonalChunkloaderOC.info("Connected: %s", this);
      }
      if (active) {
        loader = Loader.getPendingLoader(node.address());
        if (loader != null) {
          if (!loader.restore(getOwnerName(), host.world(), getHostCoord())) {
            deleteLoader();
          }
        } else {
          if (Config.chunkloaderLogLevel >= 3) {
            PersonalChunkloaderOC.info("TravelToDimension: %s", this);
          }
          UpgradeChunkloaderEnv old = activeUpgrades.get(node.address());
          if (old.hasLoader()) {
            old.deleteLoader();
          }
          createLoader(old.getOwnerName());
        }
      }
    }
  }

  @Override
  public void onDisconnect(Node node) {
    super.onDisconnect(node);
    if (node == this.node() && upgrades.remove(this)) {
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
    if (active) {
      nbt.setBoolean("active", true);
    } else if (nbt.hasKey("active")) {
      nbt.removeTag("active");
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
