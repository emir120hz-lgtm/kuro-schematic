package kuro.mixin;

import kuro.Freecam;
import kuro.Freelook;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
   @Shadow
   protected abstract void setRotation(float yaw, float pitch);

   @Shadow
   protected abstract void setPos(double x, double y, double z);

   @Shadow
   private boolean thirdPerson;

   @Inject(method = "update", at = @At("HEAD"))
   private void kuro$blend(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
      Freelook.updateBlend();
   }

   // Freelook: ilk setRotation cagrisinin (karakterin yonu) yerine freelook yonunu ver.
   // Boylece 3. sahis modunda da kamera konumu dogru hesaplanir.
   @ModifyArg(
      method = "update",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V", ordinal = 0),
      index = 0
   )
   private float kuro$yaw(float yaw) {
      return !Freecam.active && Freelook.applies() ? Freelook.camYaw(yaw) : yaw;
   }

   @ModifyArg(
      method = "update",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V", ordinal = 0),
      index = 1
   )
   private float kuro$pitch(float pitch) {
      return !Freecam.active && Freelook.applies() ? Freelook.camPitch(pitch) : pitch;
   }

   // Freecam: konum ve yonu tamamen kendi durumumuzdan al, karakteri gorunur yap.
   @Inject(method = "update", at = @At("TAIL"))
   private void kuro$freecam(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
      if (!Freecam.active) {
         return;
      }

      this.setRotation(Freecam.yaw, Freecam.pitch);
      this.setPos(
         Freecam.px + (Freecam.x - Freecam.px) * (double)tickDelta,
         Freecam.py + (Freecam.y - Freecam.py) * (double)tickDelta,
         Freecam.pz + (Freecam.z - Freecam.pz) * (double)tickDelta
      );
      this.thirdPerson = true;
   }
}
