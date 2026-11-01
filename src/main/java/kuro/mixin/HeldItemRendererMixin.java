package kuro.mixin;

import kuro.Config;
import kuro.Kuro;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
   // ViewModel (konum/olcek) + swing stilleri: eli vanilla cizimden once kaydirir / dondurur.
   @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), require = 0)
   private void kuro$viewModel(
      AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item,
      float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci
   ) {
      float side = (hand == Hand.MAIN_HAND) == (player.getMainArm() == Arm.RIGHT) ? 1.0F : -1.0F;
      if (Kuro.on[Kuro.VIEWMODEL]) {
         matrices.translate(Config.vmX * side, Config.vmY, Config.vmZ);
         float s = Config.vmScale;
         matrices.scale(s, s, s);
      }

      if (Kuro.on[Kuro.SWINGANIM] && swingProgress > 0.0F) {
         float s = MathHelper.sin(swingProgress * (float)Math.PI);
         switch ((int)Config.saStyle) {
            case 1 -> {
               matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-s * 35.0F * side));
               matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-s * 20.0F));
            }
            case 2 -> matrices.translate(0.0F, 0.0F, -s * 0.5F);
            case 3 -> matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.sin(swingProgress * (float)Math.PI * 3.0F) * 12.0F * side));
            default -> {
            }
         }
      }
   }

   // swingProgress (3. float arguman): vanilla swing kapatilabilir.
   @ModifyVariable(method = "renderFirstPersonItem", at = @At("HEAD"), ordinal = 2, argsOnly = true, require = 0)
   private float kuro$swing(float v) {
      return Kuro.on[Kuro.SWINGANIM] && Config.saVanilla < 0.5F ? 0.0F : v;
   }

   // equipProgress (4. float arguman): esya degistirme animasyonu kapatilabilir.
   @ModifyVariable(method = "renderFirstPersonItem", at = @At("HEAD"), ordinal = 3, argsOnly = true, require = 0)
   private float kuro$equip(float v) {
      return Kuro.on[Kuro.SWINGANIM] && Config.saEquip < 0.5F ? 0.0F : v;
   }
}
