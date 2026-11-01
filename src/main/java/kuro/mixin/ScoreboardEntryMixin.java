package kuro.mixin;

import kuro.ScoreboardEdit;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.text.MutableText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** FakeScoreboard: sidebar'in sagindaki skor rakamlarini da kurallardan gecir (yalnizca sidebar cizilirken). */
@Mixin(ScoreboardEntry.class)
public abstract class ScoreboardEntryMixin {
   @Inject(method = "formatted", at = @At("RETURN"), cancellable = true, require = 0)
   private void kuro$score(CallbackInfoReturnable<MutableText> cir) {
      if (ScoreboardEdit.active() && cir.getReturnValue() != null) {
         cir.setReturnValue(ScoreboardEdit.apply(cir.getReturnValue()));
      }
   }
}
