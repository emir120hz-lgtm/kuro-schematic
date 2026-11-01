package kuro.mixin;

import kuro.Freecam;
import net.minecraft.util.PlayerInput;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.input.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {
   // Freecam acikken WASD/Space/Shift karakteri degil kamerayi hareket ettirir.
   @Inject(method = "tick", at = @At("TAIL"))
   private void kuro$freeze(CallbackInfo ci) {
      if (Freecam.active) {
         this.playerInput = PlayerInput.DEFAULT;
         this.movementForward = 0.0F;
         this.movementSideways = 0.0F;
      }
   }
}
