package git.prayoadmii.lanstudio;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.WeakHashMap;
import javax.imageio.ImageIO;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.NonNull;

public final class LanWorldSettings {
	private static final String FILE_NAME = "lanstudio.properties";
	private static final Map<IntegratedServer, LanWorldSettings> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

	public int maxPlayers = 8;
	public int port = 25565;
	public boolean onlineMode = true;
	public boolean allowCommands;
	public @NonNull String motd;
	public @NonNull String iconPath = "";

	private LanWorldSettings(final IntegratedServer server) {
		String currentMotd = server.getMotd();
		
		this.motd = currentMotd == null ? server.getWorldData().getLevelName() : currentMotd;
	}

	public static LanWorldSettings get(final IntegratedServer server) {
		return CACHE.computeIfAbsent(server, LanWorldSettings::load);
	}

	public static Path file(final IntegratedServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
	}

	public static Path resolveIconPath(final IntegratedServer server, final LanWorldSettings settings) {
		if (settings.iconPath.isBlank()) {
			return null;
		}

		Path configuredPath = Path.of(settings.iconPath);

		return configuredPath.isAbsolute()
			? configuredPath
			: server.getWorldPath(LevelResource.ROOT).resolve(configuredPath).normalize();
	}

	public static Path copyAndResizeIcon(final IntegratedServer server, final Path source) throws IOException {
		try (InputStream input = Files.newInputStream(source)) {
			byte[] signature = input.readNBytes(8);

			if (signature.length != 8
				|| signature[0] != (byte)0x89
				|| signature[1] != 0x50
				|| signature[2] != 0x4e
				|| signature[3] != 0x47
				|| signature[4] != 0x0d
				|| signature[5] != 0x0a
				|| signature[6] != 0x1a
				|| signature[7] != 0x0a) {
				throw new IOException("The selected file is not a PNG image");
			}
		}

		BufferedImage input = ImageIO.read(source.toFile());

		if (input == null || input.getWidth() < 1 || input.getHeight() < 1) {
			throw new IOException("The selected file is not a readable PNG image");
		}

		BufferedImage output = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = output.createGraphics();

		try {
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.drawImage(input, 0, 0, 64, 64, null);
		} finally {
			graphics.dispose();
		}

		Path destination = server.getWorldPath(LevelResource.ROOT).resolve("lanstudio-icon.png");
		Path temporary = destination.resolveSibling("lanstudio-icon.tmp.png");

		try {
			if (!ImageIO.write(output, "png", temporary.toFile())) {
				throw new IOException("No PNG image writer is available");
			}

			try {
				Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException exception) {
				Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temporary);
		}

		return destination;
	}

	public static void save(final IntegratedServer server, final LanWorldSettings settings) throws IOException {
		Properties properties = new Properties();

		properties.setProperty("maxPlayers", Integer.toString(settings.maxPlayers));
		properties.setProperty("port", Integer.toString(settings.port));
		properties.setProperty("onlineMode", Boolean.toString(settings.onlineMode));
		properties.setProperty("allowCommands", Boolean.toString(settings.allowCommands));
		properties.setProperty("motd", settings.motd);
		properties.setProperty("iconPath", settings.iconPath);

		try (OutputStream output = Files.newOutputStream(file(server))) {
			properties.store(output, "LAN Studio settings for this world");
		}
	}

	private static LanWorldSettings load(final IntegratedServer server) {
		LanWorldSettings settings = new LanWorldSettings(server);
		Path file = file(server);

		if (!Files.isRegularFile(file)) {
			return settings;
		}

		Properties properties = new Properties();
		try (InputStream input = Files.newInputStream(file)) {
			properties.load(input);

			settings.maxPlayers = readInteger(properties, "maxPlayers", settings.maxPlayers, 1, Integer.MAX_VALUE);
			settings.port = readInteger(properties, "port", settings.port, 1, 65535);
			settings.onlineMode = readBoolean(properties, "onlineMode", settings.onlineMode);
			settings.allowCommands = readBoolean(properties, "allowCommands", settings.allowCommands);
			settings.motd = readString(properties, "motd", settings.motd);
			settings.iconPath = readString(properties, "iconPath", settings.iconPath);
		} catch (IOException | IllegalArgumentException exception) {
			LANStudio.LOGGER.error("Could not load LAN Studio settings from {}", file, exception);
		}

		return settings;
	}

	private static int readInteger(final Properties properties, final @NonNull String key, final int fallback, final int min, final int max) {
		String value = properties.getProperty(key);

		if (value == null) {
			return fallback;
		}
		try {
			int parsed = Integer.parseInt(value);

			if (parsed >= min && parsed <= max) {
				return parsed;
			}
		} catch (NumberFormatException ignored) {
			// Report invalid persisted values and keep the safe default.
		}

		LANStudio.LOGGER.warn("Ignoring invalid LAN Studio setting {}={}", key, value);

		return fallback;
	}

	private static @NonNull String readString(final Properties properties, final @NonNull String key, final @NonNull String fallback) {
		return Objects.requireNonNull(properties.getProperty(key, fallback));
	}

	private static boolean readBoolean(final Properties properties, final @NonNull String key, final boolean fallback) {
		String value = properties.getProperty(key);

		if (value == null) {
			return fallback;
		}

		if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
			return Boolean.parseBoolean(value);
		}

		LANStudio.LOGGER.warn("Ignoring invalid LAN Studio setting {}={}", key, value);

		return fallback;
	}
}
