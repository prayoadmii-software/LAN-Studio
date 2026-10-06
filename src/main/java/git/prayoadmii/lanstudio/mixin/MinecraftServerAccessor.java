package git.prayoadmii.lanstudio.mixin;

import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftServer.class)
public interface MinecraftServerAccessor {
	@Accessor("statusIcon")
	ServerStatus.@Nullable Favicon lanstudio$getStatusIcon();

	@Accessor("statusIcon")
	void lanstudio$setStatusIcon(ServerStatus.@Nullable Favicon favicon);
}
