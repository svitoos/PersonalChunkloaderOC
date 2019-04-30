package svitoos.PersonalChunkloaderOC;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Formatter;
import java.util.List;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import svitoos.PersonalChunkloaderOC.Command.User;

public class CommandDump extends CommandBase {
  @Override
  public void processCommand(final ICommandSender sender, String[] command) {
    User user;
    if (sender instanceof EntityPlayer) {
      user = s -> sender.addChatMessage(new ChatComponentText(s));
    } else {
      user = s -> PersonalChunkloaderOC.info(s);
    }
    PrintWriter printList;
    try {
      printList = new PrintWriter(PersonalChunkloaderOC.listFile);
      user.send("Chunkloders info printed to /" + PersonalChunkloaderOC.listFile.getName());
    } catch (FileNotFoundException e) {
      PersonalChunkloaderOC.logger.error("Error opening chunk loader list file", e);
      user.send("Error opening chunk loader list file");
      return;
    }
    try {
      List<Loader> loaders = Loader.getLoaders();
      if (loaders.size() > 0) {
        Formatter f = new Formatter();
        printList.println(
            f.format("Currently there are %s registered chunkloaders:", loaders.size()).toString());
      } else {
        printList.println("There is no currently registered chunkloaders.");
      }
      for (Loader loader : loaders) {
        printList.println(loader);
      }
      printList.flush();
    } finally {
      printList.close();
    }
  }

  @Override
  public String getCommandName() {
    return "pcloc_dump";
  }

  @Override
  public String getCommandUsage(ICommandSender sender) {
    return "/pcloc_dump";
  }
}
