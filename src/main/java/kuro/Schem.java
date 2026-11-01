package kuro;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;

/** Schematic okuyucu: .litematic, .schem (Sponge v2/v3) ve vanilla yapi .nbt. Hava bloklari atlanir. */
public final class Schem {
   public record Entry(int x, int y, int z, BlockState state) {
   }

   public final List<Entry> blocks = new ArrayList<>();
   public int sx;
   public int sy;
   public int sz;

   private Schem() {
   }

   public static Schem load(Path p) throws IOException {
      NbtCompound root = NbtIo.readCompressed(p, NbtSizeTracker.ofUnlimitedBytes());
      Schem s = new Schem();
      if (root.contains("Regions")) {
         s.litematic(root);
      } else if (root.contains("Schematic")) {
         s.sponge(root.getCompound("Schematic"), true);
      } else if (root.contains("BlockData") || root.contains("Blocks")) {
         s.sponge(root, false);
      } else if (root.contains("palette") && root.contains("blocks")) {
         s.structure(root);
      } else {
         throw new IOException("Desteklenmeyen format");
      }

      s.normalize();
      return s;
   }

   // ------------------------------------------------------------------ litematic
   private void litematic(NbtCompound root) {
      NbtCompound regions = root.getCompound("Regions");
      for (String key : regions.getKeys()) {
         NbtCompound r = regions.getCompound(key);
         NbtCompound pos = r.getCompound("Position");
         NbtCompound size = r.getCompound("Size");
         int rx = size.getInt("x");
         int ry = size.getInt("y");
         int rz = size.getInt("z");
         int ax = Math.abs(rx);
         int ay = Math.abs(ry);
         int az = Math.abs(rz);
         if (ax == 0 || ay == 0 || az == 0) {
            continue;
         }

         int minX = pos.getInt("x") + (rx < 0 ? rx + 1 : 0);
         int minY = pos.getInt("y") + (ry < 0 ? ry + 1 : 0);
         int minZ = pos.getInt("z") + (rz < 0 ? rz + 1 : 0);

         NbtList pal = r.getList("BlockStatePalette", NbtElement.COMPOUND_TYPE);
         BlockState[] states = new BlockState[pal.size()];
         for (int i = 0; i < states.length; i++) {
            states[i] = state(pal.getCompound(i));
         }

         long[] data = r.getLongArray("BlockStates");
         int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, states.length - 1)));
         long mask = (1L << bits) - 1L;
         long total = (long)ax * ay * az;
         for (long i = 0; i < total; i++) {
            long bit = i * bits;
            int li = (int)(bit >> 6);
            int off = (int)(bit & 63L);
            if (li >= data.length) {
               break;
            }

            long v = data[li] >>> off;
            if (off + bits > 64 && li + 1 < data.length) {
               v |= data[li + 1] << (64 - off);
            }

            int idx = (int)(v & mask);
            if (idx >= states.length || !real(states[idx])) {
               continue;
            }

            int x = (int)(i % ax);
            int z = (int)((i / ax) % az);
            int y = (int)(i / ((long)ax * az));
            blocks.add(new Entry(minX + x, minY + y, minZ + z, states[idx]));
         }
      }
   }

   // ------------------------------------------------------------------ sponge (.schem)
   private void sponge(NbtCompound c, boolean v3) {
      NbtCompound pc = c;
      NbtCompound dc = c;
      if (v3 && c.contains("Blocks")) {
         pc = c.getCompound("Blocks");
         dc = pc;
      }

      int w = c.getShort("Width") & 0xFFFF;
      int h = c.getShort("Height") & 0xFFFF;
      int l = c.getShort("Length") & 0xFFFF;
      NbtCompound palC = pc.getCompound("Palette");
      Map<Integer, BlockState> pal = new HashMap<>();
      for (String k : palC.getKeys()) {
         pal.put(palC.getInt(k), parseState(k));
      }

      byte[] data = dc.contains("Data") ? dc.getByteArray("Data") : dc.getByteArray("BlockData");
      int pos = 0;
      int i = 0;
      while (pos < data.length && w > 0 && l > 0) {
         int value = 0;
         int shift = 0;
         while (pos < data.length) {
            int b = data[pos++] & 0xFF;
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
               break;
            }

            shift += 7;
         }

         BlockState st = pal.get(value);
         if (real(st)) {
            int x = i % w;
            int z = (i / w) % l;
            int y = i / (w * l);
            blocks.add(new Entry(x, y, z, st));
         }

         i++;
      }

      if (h <= 0) {
         h = 1;
      }
   }

   // ------------------------------------------------------------------ vanilla yapi .nbt
   private void structure(NbtCompound root) {
      NbtList pal = root.getList("palette", NbtElement.COMPOUND_TYPE);
      BlockState[] states = new BlockState[pal.size()];
      for (int i = 0; i < states.length; i++) {
         states[i] = state(pal.getCompound(i));
      }

      NbtList bl = root.getList("blocks", NbtElement.COMPOUND_TYPE);
      for (int i = 0; i < bl.size(); i++) {
         NbtCompound b = bl.getCompound(i);
         NbtList p = b.getList("pos", NbtElement.INT_TYPE);
         int st = b.getInt("state");
         if (p.size() == 3 && st >= 0 && st < states.length && real(states[st])) {
            blocks.add(new Entry(p.getInt(0), p.getInt(1), p.getInt(2), states[st]));
         }
      }
   }

   // ------------------------------------------------------------------ yardimcilar
   private void normalize() {
      if (blocks.isEmpty()) {
         return;
      }

      int mx = Integer.MAX_VALUE;
      int my = Integer.MAX_VALUE;
      int mz = Integer.MAX_VALUE;
      for (Entry e : blocks) {
         mx = Math.min(mx, e.x);
         my = Math.min(my, e.y);
         mz = Math.min(mz, e.z);
      }

      List<Entry> n = new ArrayList<>(blocks.size());
      for (Entry e : blocks) {
         int x = e.x - mx;
         int y = e.y - my;
         int z = e.z - mz;
         sx = Math.max(sx, x + 1);
         sy = Math.max(sy, y + 1);
         sz = Math.max(sz, z + 1);
         n.add(new Entry(x, y, z, e.state));
      }

      n.sort(Comparator.<Entry>comparingInt(e -> e.y).thenComparingInt(e -> e.x).thenComparingInt(e -> e.z));
      blocks.clear();
      blocks.addAll(n);
   }

   private static boolean real(BlockState s) {
      return s != null && !s.isAir();
   }

   private static BlockState parseState(String s) {
      int br = s.indexOf('[');
      String name = br < 0 ? s : s.substring(0, br);
      Identifier id = Identifier.tryParse(name);
      if (id == null || !Registries.BLOCK.containsId(id)) {
         return null;
      }

      BlockState st = Registries.BLOCK.get(id).getDefaultState();
      if (br >= 0 && s.endsWith("]")) {
         for (String kv : s.substring(br + 1, s.length() - 1).split(",")) {
            int eq = kv.indexOf('=');
            if (eq > 0) {
               Property<?> prop = st.getBlock().getStateManager().getProperty(kv.substring(0, eq).trim());
               if (prop != null) {
                  st = with(st, prop, kv.substring(eq + 1).trim());
               }
            }
         }
      }

      return st;
   }

   private static BlockState state(NbtCompound c) {
      Identifier id = Identifier.tryParse(c.getString("Name"));
      if (id == null || !Registries.BLOCK.containsId(id)) {
         return null;
      }

      BlockState st = Registries.BLOCK.get(id).getDefaultState();
      if (c.contains("Properties")) {
         NbtCompound pr = c.getCompound("Properties");
         for (String k : pr.getKeys()) {
            Property<?> prop = st.getBlock().getStateManager().getProperty(k);
            if (prop != null) {
               st = with(st, prop, pr.getString(k));
            }
         }
      }

      return st;
   }

   private static <T extends Comparable<T>> BlockState with(BlockState s, Property<T> p, String v) {
      Optional<T> o = p.parse(v);
      return o.isPresent() ? s.with(p, o.get()) : s;
   }
}
