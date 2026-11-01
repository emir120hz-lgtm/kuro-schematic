package kuro;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.BedBlock;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.ComparatorBlock;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.NoteBlock;
import net.minecraft.block.RepeaterBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Oto Schematic Builder (printer): schematics/ klasorundeki .litematic / .schem / .nbt dosyasini, bakilan bloktan
 * baslayarak hotbar'daki bloklarla doldurur. Vanilla yerlestirme kullanir: blok durumu (yon, yari, eksen) oyuncunun
 * bakisina bagli ise ve hedef durum bu bakisla cikmiyorsa o blok atlanir. Eksik malzeme sohbette bildirilir.
 */
public final class SchemBuilder {
   private static final MinecraftClient mc = MinecraftClient.getInstance();
   private static final int WINDOW = 6000;

   private static volatile Schem schem;
   private static volatile boolean loading;
   private static String fileName = "";
   private static BlockPos origin;
   private static BlockRotation rot = BlockRotation.NONE;
   private static boolean[] done;
   private static int[] tried;
   private static int first;
   private static int doneCount;
   private static int tick;
   private static int cd;
   private static long lastMsg;
   private static long lastMiss;
   private static int prevSlot = -1;
   private static int breakTarget = -1;
   private static boolean mining;

   // otomatik yurume (basit pathfinding)
   private static List<BlockPos> path;
   private static int pi;
   private static int idle;
   private static int arrivedIdle;
   private static int stuck;
   private static Vec3d lastPos = Vec3d.ZERO;
   private static int skipped;
   private static Set<Long> footprint = new HashSet<>();

   private SchemBuilder() {
   }

   public static Path dir() {
      return FabricLoader.getInstance().getGameDir().resolve("schematics");
   }

   public static List<Path> files() {
      List<Path> l = new ArrayList<>();
      Path d = dir();
      try {
         Files.createDirectories(d);
         try (Stream<Path> s = Files.list(d)) {
            s.filter(p -> {
               String n = p.getFileName().toString().toLowerCase();
               return n.endsWith(".litematic") || n.endsWith(".schem") || n.endsWith(".nbt");
            }).sorted().forEach(l::add);
         }
      } catch (IOException ignored) {
      }

      return l;
   }

   private static void say(String s) {
      mc.execute(() -> {
         if (mc.player != null) {
            mc.player.sendMessage(Text.literal("[Kuro] " + s), false);
         }
      });
   }

   /** Modul acilinca: dosyayi sec, yukle, baslangic noktasini belirle. */
   public static void begin() {
      stop();
      List<Path> fs = files();
      if (fs.isEmpty()) {
         say("Schematic bulunamadı. Dosyayı şu klasöre koy: " + dir() + "  (.litematic / .schem / .nbt)");
         Kuro.setOn(Kuro.SCHEMBUILDER, false);
         return;
      }

      int idx = Math.max(1, Math.min(fs.size(), Math.round(Config.schemIndex))) - 1;
      Path f = fs.get(idx);
      fileName = f.getFileName().toString();
      rot = switch (Math.floorMod(Math.round(Config.schemRot), 4)) {
         case 1 -> BlockRotation.CLOCKWISE_90;
         case 2 -> BlockRotation.CLOCKWISE_180;
         case 3 -> BlockRotation.COUNTERCLOCKWISE_90;
         default -> BlockRotation.NONE;
      };

      // baslangic: bakilan blogun ustu, yoksa oyuncunun bulundugu blok
      HitResult hr = mc.crosshairTarget;
      if (hr instanceof BlockHitResult b && hr.getType() == HitResult.Type.BLOCK) {
         origin = b.getBlockPos().up();
      } else if (mc.player != null) {
         origin = mc.player.getBlockPos();
      } else {
         return;
      }

      loading = true;
      say("Yükleniyor: " + fileName + " (dosya " + (idx + 1) + "/" + fs.size() + ")");
      Thread t = new Thread(() -> {
         try {
            Schem s = Schem.load(f);
            if (s.blocks.isEmpty()) {
               say("Schematic boş ya da blokları tanınmadı.");
               loading = false;
               return;
            }

            done = new boolean[s.blocks.size()];
            tried = new int[s.blocks.size()];
            first = 0;
            doneCount = 0;
            Set<Long> fp = new HashSet<>();
            for (Schem.Entry e : s.blocks) {
               fp.add(worldPos(e, s).asLong());
            }

            footprint = fp;
            path = null;
            idle = 0;
            skipped = 0;
            schem = s;
            say(fileName + ": " + s.blocks.size() + " blok, boyut " + s.sx + "x" + s.sy + "x" + s.sz + ". Yerleştirme başlıyor.");
         } catch (Exception e) {
            say("Schematic okunamadı: " + e.getMessage());
         }

         loading = false;
      }, "kuro-schem");
      t.setDaemon(true);
      t.start();
   }

   public static void stop() {
      schem = null;
      loading = false;
      done = null;
      tried = null;
      path = null;
      footprint = new HashSet<>();
      breakTarget = -1;
      releaseKeys();
      if (prevSlot >= 0 && mc.player != null) {
         mc.player.getInventory().selectedSlot = prevSlot;
      }

      prevSlot = -1;
   }

   private static BlockPos worldPos(Schem.Entry e, Schem s) {
      int x = e.x();
      int z = e.z();
      int nx;
      int nz;
      switch (rot) {
         case CLOCKWISE_90 -> {
            nx = s.sz - 1 - z;
            nz = x;
         }
         case CLOCKWISE_180 -> {
            nx = s.sx - 1 - x;
            nz = s.sz - 1 - z;
         }
         case COUNTERCLOCKWISE_90 -> {
            nx = z;
            nz = s.sx - 1 - x;
         }
         default -> {
            nx = x;
            nz = z;
         }
      }

      return origin.add(nx, e.y(), nz);
   }

   public static void tick() {
      Schem s = schem;
      ClientPlayerEntity p = mc.player;
      if (s == null || p == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) {
         return;
      }

      tick++;
      if (cd > 0) {
         cd--;
         return;
      }

      double reach = Math.min(Config.schemReach, 4.5F);
      double r2 = reach * reach;
      Vec3d eye = p.getEyePos();
      int perTick = Math.max(1, Math.round(Config.schemPerTick));
      int placed = 0;
      int examined = 0;
      int reachable = 0;
      List<Schem.Entry> list = s.blocks;
      while (first < list.size() && done[first]) {
         first++;
      }

      for (int i = first; i < list.size() && examined < WINDOW && placed < perTick; i++) {
         if (done[i]) {
            continue;
         }

         examined++;
         Schem.Entry e = list.get(i);
         BlockPos wp = worldPos(e, s);
         if (eye.squaredDistanceTo(Vec3d.ofCenter(wp)) > r2 + 2.0) {
            continue;
         }

         BlockState want = e.state().rotate(rot);
         BlockState cur = mc.world.getBlockState(wp);
         if (cur.getBlock() == want.getBlock()) {
            done[i] = true;
            doneCount++;
            continue;
         }

         if (!cur.isReplaceable()) {
            if (Config.schemBreak >= 0.5F && canBreak(p, wp, cur)) {
               if (breakTarget < 0) {
                  breakTarget = i; // yanlis blok: kirilacak
               }
            } else {
               done[i] = true; // kirma kapali ya da kirilamaz (bedrock vb.): dokunma
               doneCount++;
            }

            continue;
         }

         reachable++;
         if (tick - tried[i] < 6) {
            continue;
         }

         int slot = slotOf(p, want.getBlock());
         if (slot < 0) {
            miss(want.getBlock());
            continue;
         }

         ItemStack stack = p.getInventory().getStack(slot);
         BlockHitResult hit = findFace(p, wp, want, stack, eye, reach);
         if (hit == null) {
            continue;
         }

         if (prevSlot < 0) {
            prevSlot = p.getInventory().selectedSlot;
         }

         p.getInventory().selectedSlot = slot;
         ActionResult res = mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
         tried[i] = tick;
         if (res.isAccepted()) {
            p.swingHand(Hand.MAIN_HAND);
            placed++;
         }
      }

      mining = false;
      if (placed == 0 && breakTarget >= 0) {
         mine(s, p, eye, reach);
      }

      if (mining) {
         idle = 0;
      }

      if (placed > 0) {
         cd = Math.max(0, Math.round(Config.schemDelay));
         idle = 0;
         arrivedIdle = 0;
      } else {
         idle++;
      }

      if (Config.schemWalk >= 0.5F) {
         walk(s, p);
      }

      long now = System.currentTimeMillis();
      if (now - lastMsg > 1500L) {
         lastMsg = now;
         p.sendMessage(Text.literal("Schematic: " + doneCount + "/" + list.size() + " (%" + (doneCount * 100 / Math.max(1, list.size())) + ")"
            + (reachable == 0 && doneCount < list.size() ? " - menzilde blok yok, yaklaş" : "")), true);
      }

      if (doneCount >= list.size()) {
         say("Schematic tamamlandı: " + fileName);
         Kuro.setOn(Kuro.SCHEMBUILDER, false);
      }
   }

   private static void miss(Block b) {
      long now = System.currentTimeMillis();
      if (now - lastMiss > 8000L) {
         lastMiss = now;
         say("Hotbar'da eksik: " + b.getName().getString());
      }
   }

   private static int slotOf(ClientPlayerEntity p, Block b) {
      for (int i = 0; i < 9; i++) {
         ItemStack st = p.getInventory().getStack(i);
         if (!st.isEmpty() && st.getItem() == b.asItem()) {
            return i;
         }
      }

      return -1;
   }

   private static BlockHitResult findFace(ClientPlayerEntity p, BlockPos wp, BlockState want, ItemStack stack, Vec3d eye, double reach) {
      for (Direction d : Direction.values()) {
         BlockPos np = wp.offset(d);
         BlockState ns = mc.world.getBlockState(np);
         if (ns.isAir() || ns.isReplaceable() || !ns.getFluidState().isEmpty() || interactive(ns)) {
            continue;
         }

         Vec3d hitPos = Vec3d.ofCenter(np).add(d.getOpposite().getOffsetX() * 0.5, d.getOpposite().getOffsetY() * 0.5, d.getOpposite().getOffsetZ() * 0.5);
         if (eye.distanceTo(hitPos) > reach) {
            continue;
         }

         BlockHitResult bhr = new BlockHitResult(hitPos, d.getOpposite(), np, false);
         ItemPlacementContext ctx = new ItemPlacementContext(p, Hand.MAIN_HAND, stack, bhr);
         if (!ctx.canPlace()) {
            continue;
         }

         BlockState ps = want.getBlock().getPlacementState(ctx);
         if (ps != null && same(ps, want)) {
            return bhr;
         }
      }

      return null;
   }

   private static boolean same(BlockState a, BlockState b) {
      if (a.getBlock() != b.getBlock()) {
         return false;
      }

      for (Property<?> pr : b.getProperties()) {
         if (pr != Properties.WATERLOGGED && !a.get(pr).equals(b.get(pr))) {
            return false;
         }
      }

      return true;
   }

   private static boolean interactive(BlockState s) {
      Block b = s.getBlock();
      return s.hasBlockEntity()
         || b instanceof ButtonBlock
         || b instanceof DoorBlock
         || b instanceof TrapdoorBlock
         || b instanceof FenceGateBlock
         || b instanceof LeverBlock
         || b instanceof BedBlock
         || b instanceof NoteBlock
         || b instanceof RepeaterBlock
         || b instanceof ComparatorBlock
         || b instanceof CraftingTableBlock
         || b instanceof AnvilBlock;
   }

   // ------------------------------------------------------------------ otomatik yurume

   private static void releaseKeys() {
      mc.options.forwardKey.setPressed(false);
      mc.options.jumpKey.setPressed(false);
   }

   private static void walk(Schem s, ClientPlayerEntity p) {
      boolean havePath = path != null && pi < path.size();
      if (!havePath) {
         releaseKeys();
         path = null;
         if (idle >= 6) {
            arrivedIdle++;
            if (arrivedIdle > 40) {
               // hedefe vardik ama koyamadik: bu blogu atla
               skipTarget(s);
               arrivedIdle = 0;
            }

            if (arrivedIdle % 10 == 1) {
               plan(s, p);
            }
         }

         return;
      }

      BlockPos w = path.get(pi);
      Vec3d pos = p.getPos();
      double dx = w.getX() + 0.5 - pos.x;
      double dz = w.getZ() + 0.5 - pos.z;
      if (dx * dx + dz * dz < 0.12 && Math.abs(pos.y - w.getY()) < 1.1) {
         pi++;
         stuck = 0;
         if (pi >= path.size()) {
            releaseKeys();
            path = null;
            return;
         }

         w = path.get(pi);
         dx = w.getX() + 0.5 - pos.x;
         dz = w.getZ() + 0.5 - pos.z;
      }

      float want = (float)(MathHelper.atan2(dz, dx) * 57.29577951308232) - 90.0F;
      float diff = MathHelper.wrapDegrees(want - p.getYaw());
      p.setYaw(p.getYaw() + MathHelper.clamp(diff, -45.0F, 45.0F));
      mc.options.forwardKey.setPressed(Math.abs(diff) < 70.0F);
      boolean jump = p.isOnGround() && (w.getY() > Math.floor(pos.y) || p.horizontalCollision);
      mc.options.jumpKey.setPressed(jump);

      if (pos.squaredDistanceTo(lastPos) < 0.0004) {
         stuck++;
      } else {
         stuck = 0;
      }

      lastPos = pos;
      if (stuck > 40) {
         path = null;
         stuck = 0;
         releaseKeys();
         idle = 6;
      }
   }

   private static void skipTarget(Schem s) {
      int t = pickTarget(s, mc.player);
      if (t >= 0) {
         done[t] = true;
         doneCount++;
         skipped++;
         if (skipped % 10 == 1) {
            say("Bazı bloklar atlandı (ulaşılamadı veya yön uymadı): " + skipped);
         }
      }
   }

   /** Bir sonraki hedef: en alt katmandaki, oyuncuya en yakin bitmemis blok. */
   private static int pickTarget(Schem s, ClientPlayerEntity p) {
      List<Schem.Entry> list = s.blocks;
      while (first < list.size() && done[first]) {
         first++;
      }

      if (first >= list.size()) {
         return -1;
      }

      int y0 = list.get(first).y();
      int best = -1;
      double bd = Double.MAX_VALUE;
      for (int i = first, n = 0; i < list.size() && n < 4000; i++) {
         if (done[i]) {
            continue;
         }

         Schem.Entry e = list.get(i);
         if (e.y() != y0) {
            break;
         }

         n++;
         double d = p.getPos().squaredDistanceTo(Vec3d.ofCenter(worldPos(e, s)));
         if (d < bd) {
            bd = d;
            best = i;
         }
      }

      return best;
   }

   private static void plan(Schem s, ClientPlayerEntity p) {
      int t = pickTarget(s, p);
      if (t < 0) {
         return;
      }

      BlockPos wp = worldPos(s.blocks.get(t), s);
      double reach = Math.min(Config.schemReach, 4.5F) - 0.8;
      Set<Long> goals = new HashSet<>();
      for (int dx = -3; dx <= 3; dx++) {
         for (int dz = -3; dz <= 3; dz++) {
            for (int dy = -3; dy <= 1; dy++) {
               BlockPos c = wp.add(dx, dy, dz);
               if (footprint.contains(c.asLong()) || footprint.contains(c.up().asLong()) || !standable(c)) {
                  continue;
               }

               Vec3d eye = new Vec3d(c.getX() + 0.5, c.getY() + 1.62, c.getZ() + 0.5);
               if (eye.distanceTo(Vec3d.ofCenter(wp)) <= reach) {
                  goals.add(c.asLong());
               }
            }
         }
      }

      BlockPos start = p.getBlockPos();
      if (goals.contains(start.asLong())) {
         path = null; // zaten uygun yerdeyiz; koyma denemesi surer
         return;
      }

      List<BlockPos> found = search(start, goals);
      if (found == null || found.isEmpty()) {
         return;
      }

      path = found;
      pi = 0;
      stuck = 0;
      lastPos = p.getPos();
   }

   private record Node(long key, double cost) {
   }

   /** Dijkstra (dugum butcesi sinirli): baslangictan herhangi bir hedefe giden yurunebilir yol. */
   private static List<BlockPos> search(BlockPos start, Set<Long> goals) {
      PriorityQueue<Node> pq = new PriorityQueue<>((a, b) -> Double.compare(a.cost, b.cost));
      Map<Long, Double> best = new HashMap<>();
      Map<Long, Long> prev = new HashMap<>();
      pq.add(new Node(start.asLong(), 0.0));
      best.put(start.asLong(), 0.0);
      int budget = 6000;
      int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
      while (!pq.isEmpty() && budget-- > 0) {
         Node n = pq.poll();
         if (n.cost > best.getOrDefault(n.key, Double.MAX_VALUE)) {
            continue;
         }

         if (goals.contains(n.key)) {
            List<BlockPos> out = new ArrayList<>();
            long k = n.key;
            while (k != start.asLong()) {
               out.add(BlockPos.fromLong(k));
               k = prev.get(k);
            }

            Collections.reverse(out);
            return out;
         }

         BlockPos cur = BlockPos.fromLong(n.key);
         for (int[] d : dirs) {
            for (int dy = 1; dy >= -3; dy--) {
               BlockPos t = cur.add(d[0], dy, d[1]);
               if (!standable(t)) {
                  continue;
               }

               if (dy == 1 && !passable(cur.up(2))) {
                  continue;
               }

               boolean ok = true;
               for (int y = t.getY() + 2; dy < 0 && y <= cur.getY() + 1; y++) {
                  if (!passable(new BlockPos(t.getX(), y, t.getZ()))) {
                     ok = false;
                     break;
                  }
               }

               if (!ok) {
                  continue;
               }

               double c = n.cost + 1.0 + (dy == 1 ? 0.6 : 0.0) + (dy < 0 ? 0.3 * -dy : 0.0);
               long tk = t.asLong();
               if (c < best.getOrDefault(tk, Double.MAX_VALUE)) {
                  best.put(tk, c);
                  prev.put(tk, n.key);
                  pq.add(new Node(tk, c));
               }

               break; // bu yonde en yuksek uygun hucre secildi
            }
         }
      }

      return null;
   }

   private static boolean passable(BlockPos p) {
      BlockState s = mc.world.getBlockState(p);
      return s.getCollisionShape(mc.world, p).isEmpty() && s.getFluidState().isEmpty();
   }

   private static boolean standable(BlockPos feet) {
      BlockPos below = feet.down();
      BlockState b = mc.world.getBlockState(below);
      if (b.getCollisionShape(mc.world, below).isEmpty() || !b.getFluidState().isEmpty()) {
         return false;
      }

      if (b.getCollisionShape(mc.world, below).getMax(Direction.Axis.Y) > 1.0
         || b.isOf(Blocks.MAGMA_BLOCK) || b.isOf(Blocks.CACTUS) || b.isOf(Blocks.SWEET_BERRY_BUSH) || b.isOf(Blocks.CAMPFIRE)) {
         return false;
      }

      return passable(feet) && passable(feet.up());
   }

   // ------------------------------------------------------------------ yanlis bloklari kirma

   private static boolean canBreak(ClientPlayerEntity p, BlockPos pos, BlockState st) {
      if (st.isAir() || !st.getFluidState().isEmpty()) {
         return false;
      }

      return p.getAbilities().creativeMode || st.getHardness(mc.world, pos) >= 0.0F;
   }

   private static void mine(Schem s, ClientPlayerEntity p, Vec3d eye, double reach) {
      Schem.Entry e = s.blocks.get(breakTarget);
      BlockPos bp = worldPos(e, s);
      BlockState bs = mc.world.getBlockState(bp);
      if (bs.isReplaceable() || bs.getBlock() == e.state().rotate(rot).getBlock() || eye.distanceTo(Vec3d.ofCenter(bp)) > reach + 0.5) {
         breakTarget = -1;
         mc.interactionManager.cancelBlockBreaking();
         return;
      }

      // en hizli aleti sec
      int cur = p.getInventory().selectedSlot;
      int bestSlot = cur;
      float bestSpeed = p.getInventory().getStack(cur).getMiningSpeedMultiplier(bs);
      for (int i = 0; i < 9; i++) {
         float sp = p.getInventory().getStack(i).getMiningSpeedMultiplier(bs);
         if (sp > bestSpeed + 0.01F) {
            bestSpeed = sp;
            bestSlot = i;
         }
      }

      if (bestSlot != cur) {
         if (prevSlot < 0) {
            prevSlot = cur;
         }

         p.getInventory().selectedSlot = bestSlot;
      }

      Vec3d c = Vec3d.ofCenter(bp);
      Direction face = Direction.getFacing(eye.x - c.x, eye.y - c.y, eye.z - c.z);
      if (mc.interactionManager.updateBlockBreakingProgress(bp, face)) {
         p.swingHand(Hand.MAIN_HAND);
      }

      mining = true;
   }
}
