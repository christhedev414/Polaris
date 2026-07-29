package me.polarisclient.api.util.path;

import net.minecraft.util.math.BlockPos;

public interface Goal {
   boolean isFinished(BlockPos pos);

   double heuristic(BlockPos pos);
}
