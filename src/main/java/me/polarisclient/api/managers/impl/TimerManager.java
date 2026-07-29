package me.polarisclient.api.managers.impl;

import me.polarisclient.mod.Mod;

public class TimerManager extends Mod {
   private float timer = 1.0F;

   public void set(float factor) {
      if (factor < 0.1F) {
         factor = 0.1F;
      }

      this.timer = factor;
   }

   public void reset() {
      this.timer = 1.0F;
   }

   public float get() {
      return this.timer;
   }
}
