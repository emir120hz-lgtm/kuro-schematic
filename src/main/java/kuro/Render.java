package kuro;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.BuiltBuffer;
import org.joml.Matrix4f;

public class Render {
   static final MinecraftClient mc = MinecraftClient.getInstance();
   static final double MAX_DIST = 256.0;

   public static void render(WorldRenderContext ctx) {
      if (!(Kuro.on[Kuro.STORAGE] || Kuro.on[Kuro.BLOCK] || Kuro.on[Kuro.PLAYER] || Kuro.on[Kuro.ITEMESP] || Kuro.on[Kuro.NAMETAGS] || Donut.anyOn())) {
         return;
      }

      if (mc.world == null || mc.player == null) {
         return;
      }

      MatrixStack ms = ctx.matrixStack();
      if (ms == null) {
         return;
      }

      Vec3d cam = ctx.camera().getPos();
      float td = ctx.tickCounter().getTickDelta(false);
      ms.push();
      ms.translate(-cam.x, -cam.y, -cam.z);
      Matrix4f m = ms.peek().getPositionMatrix();
      RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
      RenderSystem.disableDepthTest();
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      boxCount = 0;
      Vec3d camLook = Vec3d.fromPolar(ctx.camera().getPitch(), ctx.camera().getYaw());

      if (Kuro.on[Kuro.STORAGE]) {
         RenderSystem.lineWidth(2.0F);
         BufferBuilder b = Tessellator.getInstance().begin(DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
         drawHits(b, m, Mods.storageHits, cam, camLook, 65536.0, 1.0F);
         flush(b);
      }

      if (Kuro.on[Kuro.BLOCK]) {
         RenderSystem.lineWidth(Math.max(1.0F, Config.beThick));
         BufferBuilder b = Tessellator.getInstance().begin(DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
         double r = Config.beRange;
         drawHits(b, m, Mods.blockHits, cam, camLook, r * r, Config.beAlpha / 100.0F);
         flush(b);
      }

      if (Kuro.on[Kuro.PLAYER]) {
         RenderSystem.lineWidth(Math.max(1.0F, Config.peThick));
         BufferBuilder b = Tessellator.getInstance().begin(DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
         playerEsp(b, m, cam, camLook, td);
         flush(b);
      }

      if (Kuro.on[Kuro.ITEMESP]) {
         RenderSystem.lineWidth(Math.max(1.0F, Config.ieThick));
         BufferBuilder b = Tessellator.getInstance().begin(DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
         itemEsp(b, m, cam, td);
         flush(b);
      }

      if (Donut.anyOn()) {
         Donut.render(m, cam, camLook, td);
      }

      if (Kuro.on[Kuro.NAMETAGS]) {
         nametags(ms, ctx.camera().getRotation(), cam, td);
      }

      RenderSystem.lineWidth(1.0F);
      RenderSystem.enableDepthTest();
      RenderSystem.disableBlend();
      ms.pop();
   }

   static void flush(BufferBuilder b) {
      BuiltBuffer built = b.endNullable();
      if (built != null) {
         BufferRenderer.drawWithGlobalProgram(built);
      }
   }

   private static void playerEsp(BufferBuilder b, Matrix4f m, Vec3d cam, Vec3d camLook, float td) {
      boolean box = Config.peBox >= 0.5F;
      boolean tracer = Config.peTracer >= 0.5F;
      if (!box && !tracer) {
         return;
      }

      int base = Config.hsb(Config.peHue, Config.peSat / 100.0F, 1.0F);
      float alpha = Config.peAlpha / 100.0F;
      float tAlpha = Config.peTracerAlpha / 100.0F;
      boolean health = Config.peHealth >= 0.5F;
      double range = Config.peRange;
      double range2 = range * range;
      Vec3d start = cam.add(camLook);

      for (AbstractClientPlayerEntity e : mc.world.getPlayers()) {
         if (e == mc.player) {
            continue;
         }

         Vec3d off = e.getLerpedPos(td).subtract(e.getPos());
         Box bb = e.getBoundingBox().offset(off);
         Vec3d c = bb.getCenter();
         if (c.squaredDistanceTo(cam) > range2) {
            continue;
         }

         int col = base;
         if (health) {
            float f = Math.max(0.0F, Math.min(1.0F, (e.getHealth() + e.getAbsorptionAmount()) / Math.max(1.0F, e.getMaxHealth())));
            col = Config.hsb(120.0F * f, 1.0F, 1.0F);
         }

         float r = (float)(col >> 16 & 0xFF) / 255.0F;
         float g = (float)(col >> 8 & 0xFF) / 255.0F;
         float bl = (float)(col & 0xFF) / 255.0F;
         if (box) {
            box(b, m, bb, r, g, bl, alpha);
         }

         if (tracer) {
            line(b, m, start.x, start.y, start.z, c.x, c.y, c.z, r, g, bl, tAlpha);
         }
      }
   }

   private static final net.minecraft.entity.EquipmentSlot[] ARMOR_SLOTS = {
      net.minecraft.entity.EquipmentSlot.HEAD, net.minecraft.entity.EquipmentSlot.CHEST,
      net.minecraft.entity.EquipmentSlot.LEGS, net.minecraft.entity.EquipmentSlot.FEET
   };

   private static void nametags(MatrixStack ms, org.joml.Quaternionf rot, Vec3d cam, float td) {
      net.minecraft.client.font.TextRenderer tr = mc.textRenderer;
      net.minecraft.client.render.VertexConsumerProvider.Immediate vc = mc.getBufferBuilders().getEntityVertexConsumers();
      double range2 = (double)Config.ntRange * Config.ntRange;
      int n = 0;
      for (AbstractClientPlayerEntity e : mc.world.getPlayers()) {
         if (e == mc.player || n >= 60) {
            continue;
         }

         Vec3d pos = e.getLerpedPos(td);
         double dx = pos.x - cam.x;
         double dy = pos.y - cam.y;
         double dz = pos.z - cam.z;
         double d2 = dx * dx + dy * dy + dz * dz;
         if (d2 > range2) {
            continue;
         }

         float dist = (float)Math.sqrt(d2);
         net.minecraft.text.MutableText t = net.minecraft.text.Text.literal(e.getName().getString());
         if (Config.ntHealth >= 0.5F) {
            float hp = e.getHealth() + e.getAbsorptionAmount();
            float ratio = Math.max(0.0F, Math.min(1.0F, hp / Math.max(1.0F, e.getMaxHealth())));
            t.append(net.minecraft.text.Text.literal(" " + Math.round(hp * 10.0F) / 10.0F).setStyle(net.minecraft.text.Style.EMPTY.withColor(Config.hsb(120.0F * ratio, 1.0F, 1.0F))));
         }

         if (Config.ntArmor >= 0.5F) {
            float sum = 0.0F;
            int cnt = 0;
            for (net.minecraft.entity.EquipmentSlot s : ARMOR_SLOTS) {
               net.minecraft.item.ItemStack st = e.getEquippedStack(s);
               if (!st.isEmpty() && st.getMaxDamage() > 0) {
                  sum += 1.0F - (float)st.getDamage() / (float)st.getMaxDamage();
                  cnt++;
               }
            }

            if (cnt > 0) {
               t.append(net.minecraft.text.Text.literal(" Z" + Math.round(sum / cnt * 100.0F) + "%").setStyle(net.minecraft.text.Style.EMPTY.withColor(0x66CCFF)));
            }
         }

         if (Config.ntPing >= 0.5F && mc.getNetworkHandler() != null) {
            var pe = mc.getNetworkHandler().getPlayerListEntry(e.getUuid());
            if (pe != null) {
               t.append(net.minecraft.text.Text.literal(" " + pe.getLatency() + "ms").setStyle(net.minecraft.text.Style.EMPTY.withColor(0xAAAAAA)));
            }
         }

         if (Config.ntDist >= 0.5F) {
            t.append(net.minecraft.text.Text.literal(" " + Math.round(dist) + "m").setStyle(net.minecraft.text.Style.EMPTY.withColor(0xFFD866)));
         }

         ms.push();
         ms.translate(pos.x, pos.y + (double)e.getHeight() + 0.55, pos.z);
         ms.multiply(rot);
         float s = 0.025F * Config.ntScale * Math.max(1.0F, dist / 10.0F);
         ms.scale(-s, -s, s);
         Matrix4f mat = ms.peek().getPositionMatrix();
         float w = tr.getWidth(t);
         tr.draw(t, -w / 2.0F, 0.0F, 0xFFFFFFFF, false, mat, vc, net.minecraft.client.font.TextRenderer.TextLayerType.SEE_THROUGH, 0x80000000, 0xF000F0);
         ms.pop();
         n++;
      }

      vc.draw();
   }

   private static void itemEsp(BufferBuilder b, Matrix4f m, Vec3d cam, float td) {
      double range2 = (double)Config.ieRange * Config.ieRange;
      int n = 0;
      for (net.minecraft.entity.Entity e : mc.world.getEntities()) {
         if (!(e instanceof net.minecraft.entity.ItemEntity) || n >= 400) {
            continue;
         }

         Vec3d off = e.getLerpedPos(td).subtract(e.getPos());
         Box bb = e.getBoundingBox().offset(off);
         if (bb.getCenter().squaredDistanceTo(cam) > range2) {
            continue;
         }

         box(b, m, bb, 1.0F, 0.85F, 0.2F, 1.0F);
         n++;
      }
   }

   // Kare basina cizilecek en fazla kutu sayisi (aksi halde binlerce kutu FPS'i dusurur)
   static final int MAX_BOXES = 2500;
   static int boxCount;

   static void drawHits(BufferBuilder b, Matrix4f m, java.util.Map<Long, List<Mods.Hit>> all, Vec3d cam, Vec3d look, double maxDistSq, float alpha) {
      double reach = Math.sqrt(maxDistSq) + 14.0;
      double reach2 = reach * reach;
      for (java.util.Map.Entry<Long, List<Mods.Hit>> ent : all.entrySet()) {
         // menzil disindaki chunk'in butun listesini tek hesapla atla
         long key = ent.getKey();
         double cdx = (double)(((int)(key & 0xFFFFFFFFL)) * 16 + 8) - cam.x;
         double cdz = (double)(((int)(key >>> 32)) * 16 + 8) - cam.z;
         if (cdx * cdx + cdz * cdz > reach2) {
            continue;
         }

         List<Mods.Hit> l = ent.getValue();
         for (int i = 0, n = l.size(); i < n; i++) {
            if (boxCount >= MAX_BOXES) {
               return;
            }

            Mods.Hit h = l.get(i);
            BlockPos p = h.pos();
            double dx = (double)p.getX() + 0.5 - cam.x;
            double dy = (double)p.getY() + 0.5 - cam.y;
            double dz = (double)p.getZ() + 0.5 - cam.z;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > maxDistSq) {
               continue;
            }

            // kameranin arkasinda kalanlari cizme (yaklasik gorus konisi testi)
            if (d2 > 9.0 && dx * look.x + dy * look.y + dz * look.z < 0.0) {
               continue;
            }

            int c = h.color();
            box(
               b, m,
               (double)p.getX(), (double)p.getY(), (double)p.getZ(),
               (double)(p.getX() + 1), (double)(p.getY() + 1), (double)(p.getZ() + 1),
               (float)(c >> 16 & 0xFF) / 255.0F, (float)(c >> 8 & 0xFF) / 255.0F, (float)(c & 0xFF) / 255.0F, alpha
            );
            boxCount++;
         }
      }
   }

   static void box(BufferBuilder b, Matrix4f m, Box bx, float r, float g, float bl, float a) {
      box(b, m, bx.minX, bx.minY, bx.minZ, bx.maxX, bx.maxY, bx.maxZ, r, g, bl, a);
   }

   static void box(BufferBuilder b, Matrix4f m, double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float bl, float a) {
      line(b, m, x1, y1, z1, x2, y1, z1, r, g, bl, a);
      line(b, m, x2, y1, z1, x2, y1, z2, r, g, bl, a);
      line(b, m, x2, y1, z2, x1, y1, z2, r, g, bl, a);
      line(b, m, x1, y1, z2, x1, y1, z1, r, g, bl, a);
      line(b, m, x1, y2, z1, x2, y2, z1, r, g, bl, a);
      line(b, m, x2, y2, z1, x2, y2, z2, r, g, bl, a);
      line(b, m, x2, y2, z2, x1, y2, z2, r, g, bl, a);
      line(b, m, x1, y2, z2, x1, y2, z1, r, g, bl, a);
      line(b, m, x1, y1, z1, x1, y2, z1, r, g, bl, a);
      line(b, m, x2, y1, z1, x2, y2, z1, r, g, bl, a);
      line(b, m, x2, y1, z2, x2, y2, z2, r, g, bl, a);
      line(b, m, x1, y1, z2, x1, y2, z2, r, g, bl, a);
   }

   static void line(BufferBuilder b, Matrix4f m, double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float bl, float a) {
      b.vertex(m, (float)x1, (float)y1, (float)z1).color(r, g, bl, a);
      b.vertex(m, (float)x2, (float)y2, (float)z2).color(r, g, bl, a);
   }
}
