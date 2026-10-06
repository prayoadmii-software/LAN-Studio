package git.prayoadmii.lanstudio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import javax.imageio.ImageIO;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.resources.Identifier;
import git.prayoadmii.lanstudio.mixin.MinecraftServerAccessor;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LANStudio implements ClientModInitializer {
	public static final @NonNull String MOD_ID = "lanstudio";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static final Map<IntegratedServer, Optional<ServerStatus.Favicon>> ORIGINAL_ICONS = Collections.synchronizedMap(new WeakHashMap<>());

	@Override
	public void onInitializeClient() {
		LOGGER.info("LAN Studio Client Initialized!");
	}

	public static @NonNull Identifier id(final @NonNull String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static void applyStoredSettings(final IntegratedServer server) {
		LanWorldSettings settings = LanWorldSettings.get(server);
		applySettings(server, settings);
	}

	public static void applySettings(final IntegratedServer server, final LanWorldSettings settings) {
		ORIGINAL_ICONS.computeIfAbsent(server, current ->
			Optional.ofNullable(((MinecraftServerAccessor)current).lanstudio$getStatusIcon())
		);

		server.setUsesAuthentication(settings.onlineMode);
		server.setMotd(settings.motd);

		try {
			Path iconPath = LanWorldSettings.resolveIconPath(server, settings);

			ServerStatus.Favicon favicon = iconPath == null
				? ORIGINAL_ICONS.get(server).orElse(null)
				: loadFavicon(iconPath);
			
			((MinecraftServerAccessor)server).lanstudio$setStatusIcon(favicon);
		} catch (IOException | IllegalArgumentException exception) {
			LOGGER.error("Could Not Apply The Configured LAN Server Icon For World {}", server.getWorldData().getLevelName(), exception);

			((MinecraftServerAccessor)server).lanstudio$setStatusIcon(ORIGINAL_ICONS.get(server).orElse(null));
		}

		server.invalidateStatus();
	}

	public static ServerStatus.Favicon loadFavicon(final Path path) throws IOException {
		byte[] bytes = Files.readAllBytes(path);

		if (bytes.length > 64 * 1024
			|| bytes.length < 8
			|| bytes[0] != (byte)0x89
			|| bytes[1] != 0x50
			|| bytes[2] != 0x4e
			|| bytes[3] != 0x47
			|| bytes[4] != 0x0d
			|| bytes[5] != 0x0a
			|| bytes[6] != 0x1a
			|| bytes[7] != 0x0a) {
			throw new IllegalArgumentException("Icon Must Be A PNG File No Larger Than 64 KiB");
		}

		var image = ImageIO.read(path.toFile());

		if (image == null || image.getWidth() != 64 || image.getHeight() != 64) {
			throw new IllegalArgumentException("Icon Must Be A Readable 64 x 64 PNG Image!");
		}

		return new ServerStatus.Favicon(bytes);
	}
}
