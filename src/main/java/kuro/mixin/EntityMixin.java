package kuro.mixin;

import kuro.Freecam;
import kuro.Config;
import kuro.Freelook;
import kuro.Kuro;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
   // Fare hareketini karakter yerine Freecam / Freelook kamerasina yonlendir.
   @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
   private void kuro$look(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
      if ((Object)this != MinecraftClient.getInstance().player) {
         return;
      }

      if (Freecam.active) {
         Freecam.look(cursorDeltaX, cursorDeltaY);
         ci.cancel();
      } else if (Freelook.active) {
         Freelook.look(cursorDeltaX, cursorDeltaY);
         ci.cancel();
      }
   }

   // Hitboxes: diger oyuncularin vurus kutusunu (nisan icin) biraz buyut.
   @Inject(method = "getTargetingMargin", at = @At("HEAD"), cancellable = true)
   private void kuro$margin(CallbackInfoReturnable<Float> cir) {
      Object self = this;
      if (Kuro.on[Kuro.HITBOXES] && self instanceof PlayerEntity && self != MinecraftClient.getInstance().player) {
         cir.setReturnValue(Config.hbExpand);
      }
   }
}
