package me.polarisclient.api.events.impl;

import me.polarisclient.api.events.Event;
import net.minecraft.entity.player.EntityPlayer;

public class DeathEvent extends Event {
   public final EntityPlayer player;

   public DeathEvent(EntityPlayer player) {
      this.player = player;
   }
}
