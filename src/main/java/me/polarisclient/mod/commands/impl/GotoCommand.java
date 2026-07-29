package me.polarisclient.mod.commands.impl;

import me.polarisclient.api.util.path.GoalBlock;
import me.polarisclient.api.util.path.GoalXZ;
import me.polarisclient.mod.commands.Command;
import me.polarisclient.mod.modules.impl.movement.Pathfind;

public class GotoCommand extends Command {
   public GotoCommand() {
      super("goto", new String[]{"<x> <y> <z>", "<x> <z>", "stop"});
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

         if (count == 1 && ("stop".equalsIgnoreCase(commands[0]) || "cancel".equalsIgnoreCase(commands[0]))) {
            pathfind.stop();
            Command.sendMessage("Pathing cancelled.");
         } else if (count == 2) {
            if (isInteger(commands[0]) && isInteger(commands[1])) {
               int x = Integer.parseInt(commands[0]);
               int z = Integer.parseInt(commands[1]);
               pathfind.setGoal(new GoalXZ(x, z));
               Command.sendMessage("Pathing to " + x + " " + z + ".");
            } else {
               Command.sendMessage("Invalid coordinates.");
            }
         } else if (count == 3) {
            if (isInteger(commands[0]) && isInteger(commands[1]) && isInteger(commands[2])) {
               int x = Integer.parseInt(commands[0]);
               int y = Integer.parseInt(commands[1]);
               int z = Integer.parseInt(commands[2]);
               pathfind.setGoal(new GoalBlock(x, y, z));
               Command.sendMessage("Pathing to " + x + " " + y + " " + z + ".");
            } else {
               Command.sendMessage("Invalid coordinates.");
            }
         } else {
            Command.sendMessage(getCommandPrefix() + "goto <x> <y> <z> | <x> <z> | stop");
         }
      }
   }

   private static boolean isInteger(String str) {
      if (str != null && !str.isEmpty()) {
         try {
            Integer.parseInt(str);
            return true;
         } catch (NumberFormatException var2) {
            return false;
         }
      } else {
         return false;
      }
   }
}
