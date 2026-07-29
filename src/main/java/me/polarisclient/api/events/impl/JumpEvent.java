package me.polarisclient.api.events.impl;

import me.polarisclient.api.events.Event;

public class JumpEvent extends Event {
   public final double motionX;
   public final double motionY;

   public JumpEvent(double motionX, double motionY) {
      this.motionX = motionX;
      this.motionY = motionY;
   }
}
