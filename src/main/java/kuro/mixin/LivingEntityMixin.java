package kuro.mixin;

import kuro.Config;
import kuro.Kuro;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
   // SwingAnim: el sallama suresini (sadece goruntu) yavaslat / hizlandir.
   @Inject(method = "getHandSwingDuration", at = @At("RETURN"), cancellable = true, require = 0)
   private void kuro$swingDuration(CallbackInfoReturnable<Integer> cir) {
      Object self = this;
      if (Kuro.on[Kuro.SWINGANIM] && self == MinecraftClient.getInstance().player && Config.saSlow != 1.0F) {
         cir.setReturnValue(Math.max(1, Math.round(cir.getReturnValue() * Config.saSlow)));
      }
   }
}
