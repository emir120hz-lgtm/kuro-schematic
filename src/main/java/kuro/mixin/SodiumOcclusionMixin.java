package kuro.mixin;

import kuro.ClearRender;
import kuro.Xray;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sodium uyumu: Sodium kendi yuz-cizim kontrolunu kullanir (Block.shouldDrawSide cagrilmaz).
 * Sodium yoksa bu sinif hic uygulanmaz (@Pseudo + zorunlu olmayan mixin dosyasi), oyun etkilenmez.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockOcclusionCache", remap = false)
public abstract class SodiumOcclusionMixin {
   @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
   private void kuro$sodium(BlockState selfState, BlockView view, BlockPos pos, Direction facing, CallbackInfoReturnable<Boolean> cir) {
      if (Xray.active) {
         Xray.hooked = true;
         cir.setReturnValue(Xray.visible(selfState));
      } else if (ClearRender.active && ClearRender.isHidden(selfState.getBlock())) {
         cir.setReturnValue(false);
      }
   }
}
