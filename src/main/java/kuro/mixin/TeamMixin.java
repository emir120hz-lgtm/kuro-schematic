package kuro.mixin;

import kuro.ScoreboardEdit;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** FakeScoreboard: sidebar satirlari Team.decorateName ile uretilir; yalnizca sidebar cizilirken degistir. */
@Mixin(Team.class)
public abstract class TeamMixin {
   @Inject(method = "decorateName(Lnet/minecraft/scoreboard/AbstractTeam;Lnet/minecraft/text/Text;)Lnet/minecraft/text/MutableText;", at = @At("RETURN"), cancellable = true, require = 0)
   private static void kuro$decorate(AbstractTeam team, Text name, CallbackInfoReturnable<MutableText> cir) {
      if (ScoreboardEdit.active()) {
         cir.setReturnValue(ScoreboardEdit.apply(cir.getReturnValue()));
      }
   }
}
