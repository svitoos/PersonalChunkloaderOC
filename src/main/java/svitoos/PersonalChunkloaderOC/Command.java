package svitoos.PersonalChunkloaderOC;

import java.util.Formatter;
import java.util.List;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;

class Command extends CommandBase {
  @Override
  public void processCommand(final ICommandSender sender, String[] command) {
    User user;
    if (sender instanceof EntityPlayer) {
      user = s -> sender.addChatMessage(new ChatComponentText(s));
    } else {
      user = s -> PersonalChunkloaderOC.info(s);
    }
    List<Loader> loaders = Loader.getLoaders();
    if (loaders.size() > 0) {
      Formatter f = new Formatter();
      f.format("Currently there are %s registered chunkloaders:", loaders.size());
      user.send(f.toString());
    } else {
      user.send("There is no currently registered chunkloaders.");
    }
    for (Loader loader : loaders) {
      user.send(loader.toString());
    }
  }

  @Override
  public String getCommandName() {
    return "pcloc_list";
  }

  @Override
  public String getCommandUsage(ICommandSender sender) {
    return "/pcloc_list help";
  }

  interface User {
    void send(String s);
  }
}
