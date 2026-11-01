package kuro.mixin;

import kuro.Config;
import kuro.Kuro;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
   // Velocity: sunucunun bize verdiği itme (knockback) paketini engelle / azalt.
   @Inject(method = "onEntityVelocityUpdate", at = @At("HEAD"), cancellable = true)
   private void kuro$velocity(EntityVelocityUpdateS2CPacket packet, CallbackInfo ci) {
      MinecraftClient mc = MinecraftClient.getInstance();
      // ag thread'inde dokunma; vanilla paketi ana thread'e yeniden kuyruklar ve burasi tekrar cagrilir
      if (!Kuro.on[Kuro.VELOCITY] || !mc.isOnThread() || mc.player == null || packet.getEntityId() != mc.player.getId()) {
         return;
      }

      ci.cancel();
      double keep = Config.velocityKeep / 100.0;
      if (keep > 0.0) {
         mc.player.setVelocityClient(packet.getVelocityX() * keep, packet.getVelocityY() * keep, packet.getVelocityZ() * keep);
      }
   }
}
