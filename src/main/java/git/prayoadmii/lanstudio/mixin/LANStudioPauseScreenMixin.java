package git.prayoadmii.lanstudio.mixin;

import git.prayoadmii.lanstudio.LanSettingsScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class LANStudioPauseScreenMixin {
	@Inject(method = "createPauseMenu", at = @At("TAIL"))
	private void lanstudio$addLanSettingsButton(final CallbackInfo callback) {
		ScreenAccessor screenAccessor = (ScreenAccessor)(Object)this;

		if (screenAccessor.lanstudio$getMinecraft().getSingleplayerServer() == null || screenAccessor.lanstudio$getMinecraft().player == null) {
			return;
		}

		int buttonY = Math.max(0, screenAccessor.lanstudio$getHeight() - 30);
		
		screenAccessor.lanstudio$addRenderableWidget(
			Button.builder(Component.literal("LAN Studio"), button ->
				screenAccessor.lanstudio$getMinecraft().gui.setScreen(new LanSettingsScreen((Screen)(Object)this))
			).bounds(screenAccessor.lanstudio$getWidth() / 2 - 90, buttonY, 180, 20).build()
		);
	}
}
