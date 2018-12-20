package svitoos.PersonalChunkloaderOC;

import com.google.common.collect.ImmutableSet;
import java.util.HashMap;
import java.util.List;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import java.util.Map;
import li.cil.oc.api.event.RobotMoveEvent;
import li.cil.oc.api.network.Node;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.PlayerOrderedLoadingCallback;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

public class UpgradeChunkloaderHandler implements PlayerOrderedLoadingCallback {

  @Override
  public ListMultimap<String, Ticket> playerTicketsLoaded(
      ListMultimap<String, Ticket> tickets, World world) {

    ListMultimap<String, Ticket> loaded = ArrayListMultimap.create();

    if (UpgradeChunkloaderEnv.allowedDim(world.provider.dimensionId)) {
      tickets
          .keySet()
          .forEach(
              playerName -> {
                List<Ticket> playerTickets = tickets.get(playerName);
                final int ticketCountAvailable =
                    ForgeChunkManager.ticketCountAvailableFor(playerName);
                int ticketCount = 0;
                for (Ticket fcmTicket : playerTickets) {
                  if (UpgradeChunkloaderTicket.isValid(fcmTicket)
                      && UpgradeChunkloaderEnv.allowedCoord(
                          new UpgradeChunkloaderTicket(fcmTicket).getBlockCoord())) {
                    ticketCount++;
                    if (ticketCount > ticketCountAvailable) {
                      break;
                    }
                    loaded.put(fcmTicket.getPlayerName(), fcmTicket);
                  }
                }
              });
    }
    return loaded;
  }

  static Map<String, UpgradeChunkloaderTicket> pendingTickets = new HashMap<>();

  @Override
  public void ticketsLoaded(List<Ticket> fcmTickets, World world) {
    fcmTickets.forEach(
        fcmTicket -> {
          UpgradeChunkloaderTicket ticket = new UpgradeChunkloaderTicket(fcmTicket);
          PersonalChunkloaderOC.info("Restoring %s", ticket);
          pendingTickets.put(ticket.address, ticket);
        });
    ImmutableSet.copyOf(pendingTickets.values()).forEach(UpgradeChunkloaderTicket::forceLoad);
    pendingTickets
        .values()
        .forEach(
            ticket -> {
              PersonalChunkloaderOC.warn("A chunkloader ticket has been orphaned! : %s", ticket);
              ticket.release();
            });
    pendingTickets.clear();
  }

  @SubscribeEvent
  public void onRobotMove(RobotMoveEvent.Post e) {
    final Node machineNode = e.agent.machine().node();
    machineNode
        .reachableNodes()
        .forEach(
            node -> {
              if (node.host() instanceof UpgradeChunkloaderEnv) {
                UpgradeChunkloaderEnv.onRobotMove((UpgradeChunkloaderEnv) node.host());
              }
            });
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST) // before UpgradeChunkloader.onDisconnect
  public void onChunkUnload(ChunkEvent.Unload e) {
    UpgradeChunkloaderEnv.onChunkUnload(
        e.getChunk().worldObj.provider.dimensionId, e.getChunk().getChunkCoordIntPair());
  }

  @SubscribeEvent(priority = EventPriority.HIGH) // after ForgeChunkManager
  public void onWorldLoad(WorldEvent.Load e) {
    UpgradeChunkloaderEnv.onWorldLoad(e.world.provider.dimensionId);
  }

  @SubscribeEvent(
      priority =
          EventPriority.HIGH) // after ForgeChunkManager, before UpgradeChunkloader.onDisconnect
  public void onWorldUnload(WorldEvent.Unload e) {
    UpgradeChunkloaderEnv.onWorldUnload(e.world.provider.dimensionId);
  }

  @SubscribeEvent
  public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent e) {
    UpgradeChunkloaderEnv.onPlayerLoggedIn(e.player.getCommandSenderName());
  }

  @SubscribeEvent
  public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent e) {
    UpgradeChunkloaderEnv.onPlayerLoggedOut(e.player.getCommandSenderName());
  }
}
