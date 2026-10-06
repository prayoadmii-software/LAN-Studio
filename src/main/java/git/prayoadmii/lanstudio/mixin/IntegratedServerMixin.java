package git.prayoadmii.lanstudio.mixin;

import git.prayoadmii.lanstudio.LANStudio;
import git.prayoadmii.lanstudio.LanWorldSettings;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin {
	@Inject(method = "getMaxPlayers", at = @At("HEAD"), cancellable = true)
	private void lanstudio$useConfiguredPlayerLimit(final CallbackInfoReturnable<Integer> callback) {
		callback.setReturnValue(LanWorldSettings.get((IntegratedServer)(Object)this).maxPlayers);
	}

	@Inject(method = "initServer", at = @At("TAIL"))
	private void lanstudio$applySavedSettings(final CallbackInfoReturnable<Boolean> callback) {
		if (callback.getReturnValueZ()) {
			LANStudio.applyStoredSettings((IntegratedServer)(Object)this);
		}
	}
}
