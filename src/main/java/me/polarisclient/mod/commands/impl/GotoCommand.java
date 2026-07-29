package me.polarisclient.mod.commands.impl;

import me.polarisclient.api.util.path.goal.GoalBlock;
import me.polarisclient.api.util.path.goal.GoalEntity;
import me.polarisclient.api.util.path.goal.GoalRadius;
import me.polarisclient.api.util.path.goal.GoalXZ;
import me.polarisclient.api.util.path.goal.GoalY;
import me.polarisclient.mod.commands.Command;
import me.polarisclient.mod.modules.impl.misc.Pathfind;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Sets the pathfinder's destination.
 *
 *   goto &lt;x&gt; &lt;z&gt;              - reach that column at any height
 *   goto &lt;x&gt; &lt;y&gt; &lt;z&gt;          - stand on exactly that block
 *   goto &lt;x&gt; &lt;y&gt; &lt;z&gt; &lt;r&gt;      - get within r blocks of it
 *   goto y &lt;level&gt;              - reach that Y level
 *   goto follow &lt;player&gt;        - follow a player around
 *   goto stop                   - cancel
 *
 * Note the argument array arrives already shifted by CommandManager, so index 0 is the first real
 * argument and the array is null-terminated rather than exactly sized.
 */
public class GotoCommand extends Command {
   public GotoCommand() {
      super("goto", new String[]{"<x> <y> <z>", "<x> <z>", "y <level>", "follow <player>", "stop"});
   }

   @Override
   public void execute(String[] commands) {
      Pathfind pathfind = Pathfind.INSTANCE;
      if (pathfind == null) {
         Command.sendMessage("Pathfind is unavailable.");
      } else {
         int count = 0;

         while(count < commands.length && commands[count] != null) {
            ++count;
         }

         if (count == 0) {
            this.usage();
         } else if (count == 1 && isKeyword(commands[0], "stop", "cancel")) {
            pathfind.cancel();
            Command.sendMessage("Pathing cancelled.");
         } else if (count == 2 && isKeyword(commands[0], "y", "level")) {
            this.gotoY(pathfind, commands[1]);
         } else if (count == 2 && isKeyword(commands[0], "follow")) {
            this.follow(pathfind, commands[1]);
         } else if (count == 2) {
            this.gotoXZ(pathfind, commands[0], commands[1]);
         } else if (count == 3) {
            this.gotoBlock(pathfind, commands[0], commands[1], commands[2]);
         } else if (count == 4) {
            this.gotoRadius(pathfind, commands[0], commands[1], commands[2], commands[3]);
         } else {
            this.usage();
         }
      }
   }

   private void gotoXZ(Pathfind pathfind, String xArg, String zArg) {
      Integer x = parseInt(xArg);
      Integer z = parseInt(zArg);
      if (x != null && z != null) {
         pathfind.pathTo(new GoalXZ(x, z));
         Command.sendMessage("Pathing to " + x + " " + z + ".");
      } else {
         Command.sendMessage("Invalid coordinates.");
      }
   }

   private void gotoBlock(Pathfind pathfind, String xArg, String yArg, String zArg) {
      Integer x = parseInt(xArg);
      Integer y = parseInt(yArg);
      Integer z = parseInt(zArg);
      if (x != null && y != null && z != null) {
         pathfind.pathTo(new GoalBlock(x, y, z));
         Command.sendMessage("Pathing to " + x + " " + y + " " + z + ".");
      } else {
         Command.sendMessage("Invalid coordinates.");
      }
   }

   private void gotoRadius(Pathfind pathfind, String xArg, String yArg, String zArg, String radiusArg) {
      Integer x = parseInt(xArg);
      Integer y = parseInt(yArg);
      Integer z = parseInt(zArg);
      Integer radius = parseInt(radiusArg);
      if (x != null && y != null && z != null && radius != null) {
         pathfind.pathTo(new GoalRadius(x, y, z, (double)radius.intValue()));
         Command.sendMessage("Pathing to within " + radius + " of " + x + " " + y + " " + z + ".");
      } else {
         Command.sendMessage("Invalid coordinates.");
      }
   }

   private void gotoY(Pathfind pathfind, String yArg) {
      Integer y = parseInt(yArg);
      if (y != null) {
         pathfind.pathTo(new GoalY(y));
         Command.sendMessage("Pathing to y " + y + ".");
      } else {
         Command.sendMessage("Invalid level.");
      }
   }

   private void follow(Pathfind pathfind, String name) {
      if (mc.world == null) {
         Command.sendMessage("Not in a world.");
      } else {
         for(EntityPlayer player : mc.world.playerEntities) {
            if (player != mc.player && player.getName().equalsIgnoreCase(name)) {
               pathfind.pathTo(new GoalEntity(player, 2.0));
               Command.sendMessage("Following " + player.getName() + ".");
               return;
            }
         }

         Command.sendMessage("No player named " + name + " nearby.");
      }
   }

   private void usage() {
      Command.sendMessage(getCommandPrefix() + "goto <x> [y] <z> [radius] | y <level> | follow <player> | stop");
   }

   private static boolean isKeyword(String argument, String... keywords) {
      for(String keyword : keywords) {
         if (keyword.equalsIgnoreCase(argument)) {
            return true;
         }
      }

      return false;
   }

   /** Returns null rather than throwing, so callers can report one clean error message. */
   private static Integer parseInt(String value) {
      if (value != null && !value.isEmpty()) {
         try {
            return Integer.valueOf(value);
         } catch (NumberFormatException var2) {
            return null;
         }
      } else {
         return null;
      }
   }
}
