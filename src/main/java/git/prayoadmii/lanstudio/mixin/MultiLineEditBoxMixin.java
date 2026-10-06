package git.prayoadmii.lanstudio.mixin;

import git.prayoadmii.lanstudio.LanSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.input.CharacterEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiLineEditBox.class)
public abstract class MultiLineEditBoxMixin {
	@Shadow
	@Final
	private MultilineTextField textField;

	@Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
	private void lanstudio$acceptSectionSign(final CharacterEvent event, final CallbackInfoReturnable<Boolean> callback) {
		if (event.codepoint() == '\u00a7'
			&& this.textField != null
			&& Minecraft.getInstance().gui.screen() instanceof LanSettingsScreen
			&& ((MultiLineEditBox)(Object)this).isFocused()) {
			this.textField.insertText("\u00a7");
			callback.setReturnValue(true);
		}
	}
}
