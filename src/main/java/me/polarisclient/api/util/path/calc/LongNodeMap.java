package me.polarisclient.api.util.path.calc;

/**
 * An open-addressing hash map from packed block position to {@link PathNode}.
 *
 * HashMap&lt;Long, PathNode&gt; would box a Long for every lookup, and A* looks up every neighbour of
 * every expanded node - that is millions of short-lived objects on a long search. This stores the
 * keys as primitive longs in a flat array, so a lookup allocates nothing.
 *
 * Linear probing is used rather than chaining because the probe sequence stays in cache. An empty
 * slot is identified by a null VALUE rather than a sentinel key, since 0 is a legitimate key
 * (the block at the world origin).
 */
public final class LongNodeMap {
   private static final double LOAD_FACTOR = 0.7;
   /** Fibonacci hashing constant: scatters sequential keys, which packed coordinates very much are. */
   private static final long MIX = -7046029254386353131L;

   private long[] keys;
   private PathNode[] values;
   private int mask;
   private int size;
   private int threshold;

   public LongNodeMap(int expectedSize) {
      int capacity = tableSizeFor((int)((double)Math.max(16, expectedSize) / LOAD_FACTOR));
      this.keys = new long[capacity];
      this.values = new PathNode[capacity];
      this.mask = capacity - 1;
      this.threshold = (int)((double)capacity * LOAD_FACTOR);
   }

   public int size() {
      return this.size;
   }

   public PathNode get(long key) {
      int index = index(key);

      while(this.values[index] != null) {
         if (this.keys[index] == key) {
            return this.values[index];
         }

         index = index + 1 & this.mask;
      }

      return null;
   }

   public void put(long key, PathNode node) {
      int index = index(key);

      while(this.values[index] != null) {
         if (this.keys[index] == key) {
            this.values[index] = node;
            return;
         }

         index = index + 1 & this.mask;
      }

      this.keys[index] = key;
      this.values[index] = node;
      if (++this.size > this.threshold) {
         this.rehash();
      }
   }

   private void rehash() {
      long[] oldKeys = this.keys;
      PathNode[] oldValues = this.values;
      int capacity = oldValues.length << 1;
      this.keys = new long[capacity];
      this.values = new PathNode[capacity];
      this.mask = capacity - 1;
      this.threshold = (int)((double)capacity * LOAD_FACTOR);

      for(int i = 0; i < oldValues.length; ++i) {
         if (oldValues[i] != null) {
            int index = index(oldKeys[i]);

            while(this.values[index] != null) {
               index = index + 1 & this.mask;
            }

            this.keys[index] = oldKeys[i];
            this.values[index] = oldValues[i];
         }
      }
   }

   private int index(long key) {
      long hash = key * MIX;
      return (int)(hash ^ hash >>> 32) & this.mask;
   }

   private static int tableSizeFor(int target) {
      int capacity = 16;

      while(capacity < target) {
         capacity <<= 1;
      }

      return capacity;
   }
}
