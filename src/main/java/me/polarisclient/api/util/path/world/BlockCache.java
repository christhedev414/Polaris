package me.polarisclient.api.util.path.world;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.MutableBlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

/**
 * A thread-safe snapshot of the world's terrain, stored as one {@link BlockType} ordinal per block.
 *
 * This exists because Minecraft's World is not safe to read from a worker thread - the network
 * thread mutates chunks as block updates arrive, so an off-thread getBlockState can observe a
 * torn chunk section or trip a chunk load. Instead:
 *
 *   - {@link #captureChunk} runs on the MAIN thread and builds a fresh byte[] for one chunk.
 *   - The finished array is published into a ConcurrentHashMap and never mutated again.
 *   - {@link #getType} reads those arrays from ANY thread.
 *
 * Because arrays are replaced rather than edited, a reader either sees the whole old snapshot or
 * the whole new one, never a half-written mix. Chunks that have not been captured read as
 * {@link BlockType#UNKNOWN}, which the movement rules treat as impassable - so a stale or cold
 * cache makes the pathfinder conservative rather than wrong.
 */
public class BlockCache {
   /** Minecraft worlds are 256 blocks tall, so a chunk column is 16 * 256 * 16 bytes = 64 KiB. */
   public static final int WORLD_HEIGHT = 256;
   private static final int CHUNK_BYTES = 16 * WORLD_HEIGHT * 16;

   private final Map<Long, byte[]> chunks = new ConcurrentHashMap<>();
   private final Map<Long, Long> captureTimes = new ConcurrentHashMap<>();

   /**
    * Captures one chunk into the cache. Main thread only.
    *
    * Empty chunk sections are skipped rather than read block by block, which is what keeps this
    * affordable: a typical overworld chunk has only four or five non-empty sections out of sixteen,
    * so this touches roughly 20k blocks instead of 65k.
    */
   public void captureChunk(World world, Chunk chunk) {
      byte[] data = new byte[CHUNK_BYTES];
      ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
      MutableBlockPos pos = new MutableBlockPos();
      int baseX = chunk.x << 4;
      int baseZ = chunk.z << 4;

      for(int section = 0; section < sections.length; ++section) {
         ExtendedBlockStorage storage = sections[section];
         // A null or empty section is pure air, and the array is already zero-filled with UNKNOWN,
         // so write AIR across it explicitly and move on.
         if (storage == null || storage.isEmpty()) {
            fillSection(data, section, (byte)BlockType.AIR.ordinal());
            continue;
         }

         int sectionBaseY = section << 4;

         for(int y = 0; y < 16; ++y) {
            for(int x = 0; x < 16; ++x) {
               for(int z = 0; z < 16; ++z) {
                  IBlockState state = storage.get(x, y, z);
                  pos.setPos(baseX + x, sectionBaseY + y, baseZ + z);
                  data[index(x, sectionBaseY + y, z)] = (byte)BlockType.classify(state, world, pos).ordinal();
               }
            }
         }
      }

      long key = chunkKey(chunk.x, chunk.z);
      this.chunks.put(key, data);
      this.captureTimes.put(key, System.currentTimeMillis());
   }

   private static void fillSection(byte[] data, int section, byte value) {
      int baseY = section << 4;

      for(int y = baseY; y < baseY + 16; ++y) {
         int start = index(0, y, 0);

         for(int i = start; i < start + 256; ++i) {
            data[i] = value;
         }
      }
   }

   /** Reads a block's category. Safe from any thread. */
   public BlockType getType(int x, int y, int z) {
      if (y < 0 || y >= WORLD_HEIGHT) {
         return BlockType.UNKNOWN;
      } else {
         byte[] data = this.chunks.get(chunkKey(x >> 4, z >> 4));
         return data == null ? BlockType.UNKNOWN : BlockType.byOrdinal(data[index(x & 15, y, z & 15)]);
      }
   }

   public BlockType getType(BlockPos pos) {
      return this.getType(pos.getX(), pos.getY(), pos.getZ());
   }

   public boolean isCached(int chunkX, int chunkZ) {
      return this.chunks.containsKey(chunkKey(chunkX, chunkZ));
   }

   /** Age of a chunk's snapshot in milliseconds, or Long.MAX_VALUE if it was never captured. */
   public long getAge(int chunkX, int chunkZ) {
      Long time = this.captureTimes.get(chunkKey(chunkX, chunkZ));
      return time == null ? Long.MAX_VALUE : System.currentTimeMillis() - time;
   }

   /** Forgets chunks further than {@code radius} chunks from the given centre, bounding memory use. */
   public void evictOutside(int centreChunkX, int centreChunkZ, int radius) {
      Iterator<Map.Entry<Long, byte[]>> iterator = this.chunks.entrySet().iterator();
      List<Long> removed = new ArrayList<>();

      while(iterator.hasNext()) {
         Long key = iterator.next().getKey();
         int chunkX = (int)(key >> 32);
         int chunkZ = key.intValue();
         if (Math.abs(chunkX - centreChunkX) > radius || Math.abs(chunkZ - centreChunkZ) > radius) {
            iterator.remove();
            removed.add(key);
         }
      }

      for(Long key : removed) {
         this.captureTimes.remove(key);
      }
   }

   public void clear() {
      this.chunks.clear();
      this.captureTimes.clear();
   }

   public int getCachedChunkCount() {
      return this.chunks.size();
   }

   /** Approximate heap footprint of the cache, in bytes. */
   public long getMemoryUsage() {
      return (long)this.chunks.size() * (long)CHUNK_BYTES;
   }

   private static int index(int x, int y, int z) {
      return (y << 8) | (x << 4) | z;
   }

   private static long chunkKey(int chunkX, int chunkZ) {
      return (long)chunkX << 32 | (long)chunkZ & 4294967295L;
   }
}
