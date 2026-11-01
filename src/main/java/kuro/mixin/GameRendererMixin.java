package kuro.mixin;

import kuro.Kuro;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
   // NoHurtCam: hasar aldiginda kameranin sallanmasini kapat.
   @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true, require = 0)
   private void kuro$noHurtCam(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
      if (Kuro.on[Kuro.NOHURTCAM]) {
         ci.cancel();
      }
   }
}
