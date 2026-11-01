package kuro.mixin;

import kuro.ClearRender;
import kuro.Xray;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public abstract class BlockMixin {
   // XRay: secili bloklar her yuzeyiyle cizilir, digerleri hic cizilmez.
   @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true)
   private static void kuro$xray(BlockState state, BlockState otherState, Direction side, CallbackInfoReturnable<Boolean> cir) {
      if (Xray.active) {
         Xray.hooked = true;
         cir.setReturnValue(Xray.visible(state));
      }
   }

   // ClearRender: secilen dolgu bloklari cizilmez
   @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true)
   private static void kuro$clear(BlockState state, BlockState otherState, Direction side, CallbackInfoReturnable<Boolean> cir) {
      if (ClearRender.active && !Xray.active && ClearRender.isHidden(state.getBlock())) {
         cir.setReturnValue(false);
      }
   }
}
