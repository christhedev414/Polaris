package me.polarisclient.api.util.path.calc;

/**
 * A min-heap of {@link PathNode} ordered by fCost, with support for decrease-key.
 *
 * java.util.PriorityQueue cannot do this. When A* finds a cheaper route to a node already queued,
 * the queue has to be re-ordered; PriorityQueue offers no way to say "this element's priority fell",
 * so the usual workaround is to insert a duplicate and discard stale copies at poll time. That
 * inflates the heap and wastes comparisons.
 *
 * Here each node remembers its own index in the array, so {@link #decreaseKey} finds it in O(1) and
 * fixes the ordering in O(log n) - no duplicates, no stale entries.
 */
public final class BinaryHeap {
   private PathNode[] heap;
   private int size;

   public BinaryHeap(int initialCapacity) {
      this.heap = new PathNode[Math.max(16, initialCapacity)];
   }

   public int size() {
      return this.size;
   }

   public boolean isEmpty() {
      return this.size == 0;
   }

   public void insert(PathNode node) {
      if (this.size == this.heap.length) {
         PathNode[] grown = new PathNode[this.heap.length << 1];
         System.arraycopy(this.heap, 0, grown, 0, this.heap.length);
         this.heap = grown;
      }

      this.heap[this.size] = node;
      node.heapIndex = this.size;
      this.siftUp(this.size++);
   }

   /** Call after a node's fCost has been lowered while it is queued. */
   public void decreaseKey(PathNode node) {
      if (node.heapIndex >= 0) {
         this.siftUp(node.heapIndex);
      }
   }

   public PathNode poll() {
      if (this.size == 0) {
         return null;
      } else {
         PathNode root = this.heap[0];
         root.heapIndex = -1;
         if (--this.size > 0) {
            this.heap[0] = this.heap[this.size];
            this.heap[0].heapIndex = 0;
            this.siftDown(0);
         }

         this.heap[this.size] = null;
         return root;
      }
   }

   public void clear() {
      for(int i = 0; i < this.size; ++i) {
         if (this.heap[i] != null) {
            this.heap[i].heapIndex = -1;
            this.heap[i] = null;
         }
      }

      this.size = 0;
   }

   private void siftUp(int index) {
      PathNode node = this.heap[index];
      double cost = node.fCost;

      while(index > 0) {
         int parent = index - 1 >>> 1;
         if (this.heap[parent].fCost <= cost) {
            break;
         }

         this.heap[index] = this.heap[parent];
         this.heap[index].heapIndex = index;
         index = parent;
      }

      this.heap[index] = node;
      node.heapIndex = index;
   }

   private void siftDown(int index) {
      PathNode node = this.heap[index];
      double cost = node.fCost;
      int half = this.size >>> 1;

      while(index < half) {
         int child = (index << 1) + 1;
         int right = child + 1;
         if (right < this.size && this.heap[right].fCost < this.heap[child].fCost) {
            child = right;
         }

         if (this.heap[child].fCost >= cost) {
            break;
         }

         this.heap[index] = this.heap[child];
         this.heap[index].heapIndex = index;
         index = child;
      }

      this.heap[index] = node;
      node.heapIndex = index;
   }
}
