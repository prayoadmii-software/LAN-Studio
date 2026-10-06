package git.prayoadmii.lanstudio.mixin;

import git.prayoadmii.lanstudio.LanSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.util.StringUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(MultilineTextField.class)
public abstract class MultilineTextFieldMixin {
	@ModifyArg(
		method = "insertText",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/util/StringUtil;filterText(Ljava/lang/String;Z)Ljava/lang/String;"
		),
		index = 0
	)
	private String lanstudio$preserveSectionSign(final String input) {
		if (!(Minecraft.getInstance().gui.screen() instanceof LanSettingsScreen) || input.indexOf('\u00a7') < 0) {
			return input;
		}

		StringBuilder filtered = new StringBuilder(input.length());
		
		for (int i = 0; i < input.length(); i++) {
			char character = input.charAt(i);

			if (character == '\u00a7' || StringUtil.isAllowedChatCharacter(character) || character == '\n') {
				filtered.append(character);
			}
		}

		return filtered.toString();
	}
}
