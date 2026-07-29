//Deobfuscated with https://github.com/SimplyProgrammer/Minecraft-Deobfuscator3000 using mappings "G:\PortableSoft\JBY\MC_Deobf3000\1.12-MCP-Mappings"!

package me.polarisclient.api.managers.impl;

import com.mojang.realmsclient.gui.ChatFormatting;
import me.polarisclient.Polaris;
import me.polarisclient.api.events.impl.PacketEvent;
import me.polarisclient.mod.Mod;
import me.polarisclient.mod.commands.Command;
import net.minecraft.network.play.client.CPacketChatMessage;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class ReloadManager extends Mod {
   public String prefix;

   public void init(String prefix) {
      this.prefix = prefix;
      MinecraftForge.EVENT_BUS.register(this);
      if (!fullNullCheck()) {
         Command.sendMessage(ChatFormatting.RED + "Polaris" + " has been unloaded. Type " + prefix + "reload to reload.");
      }
   }

   public void unload() {
      MinecraftForge.EVENT_BUS.unregister(this);
   }

   @SubscribeEvent
   public void onPacketSend(PacketEvent.Send event) {
      CPacketChatMessage packet;
      if (event.getPacket() instanceof CPacketChatMessage
         && (packet = event.getPacket()).getMessage().startsWith(this.prefix)
         && packet.getMessage().contains("reload")) {
         Polaris.load();
         event.setCanceled(true);
      }
   }
}
