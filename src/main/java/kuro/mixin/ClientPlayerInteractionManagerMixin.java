package kuro.mixin;

import kuro.FakePlayer;
import kuro.Kuro;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {
   // CrystalOptimizer: kristale vurur vurmaz istemcide kaldır; sunucuyu beklemeden hemen yenisini koyabilirsin.
   @Inject(method = "attackEntity", at = @At("TAIL"))
   private void kuro$crystalOptimizer(PlayerEntity player, Entity target, CallbackInfo ci) {
      if (!Kuro.on[Kuro.CRYSTALOPT] || !(target instanceof EndCrystalEntity) || player != MinecraftClient.getInstance().player) {
         return;
      }

      target.setRemoved(Entity.RemovalReason.KILLED);
      target.onRemoved();
   }

   // FakePlayer: sahte oyuncuya vurus sunucuya gitmesin, hasar istemcide hesaplansin.
   @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
   private void kuro$fakeHit(PlayerEntity player, Entity target, CallbackInfo ci) {
      if (FakePlayer.melee(player, target)) {
         ci.cancel();
      }
   }
}
