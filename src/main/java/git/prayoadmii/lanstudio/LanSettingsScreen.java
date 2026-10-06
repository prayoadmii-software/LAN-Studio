package git.prayoadmii.lanstudio;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.NonNull;

public final class LanSettingsScreen extends Screen {
	private static final int DESIGN_WIDTH = 360;
	private static final int NORMAL_HEIGHT = 328;
	private static final int NARROW_HEIGHT = 374;
	private static final @NonNull Identifier ICON_PREVIEW_TEXTURE = LANStudio.id("icon_preview");
	private final Screen parent;
	private final IntegratedServer server;
	private final LanWorldSettings settings;
	private int contentWidth;
	private float layoutScale;
	private int previewX;
	private int previewY;
	private int previewSize;
	private MultiLineEditBox motdField;
	private EditBox playerLimitField;
	private EditBox portField;
	private StringWidget statusLabel;
	private Button lanButton;
	private boolean narrow;
	private boolean iconPreviewLoaded;

	public LanSettingsScreen(final Screen parent) {
		super(Component.literal("LAN Studio"));
		this.parent = parent;
		this.server = this.minecraft.getSingleplayerServer();
		
		if (this.server == null) {
			throw new IllegalStateException("LAN settings can only be opened while playing a singleplayer world");
		}

		this.settings = LanWorldSettings.get(this.server);
	}

	@Override
	protected void init() {
		this.narrow = this.width < DESIGN_WIDTH;

		int designHeight = this.narrow ? NARROW_HEIGHT : NORMAL_HEIGHT;

		this.layoutScale = Math.min(
			1.0F,
			Math.min(Math.max(0, this.width - 24) / (float)DESIGN_WIDTH, Math.max(0, this.height - 20) / (float)designHeight)
		);

		this.contentWidth = Math.round(DESIGN_WIDTH * this.layoutScale);

		int left = (this.width - this.contentWidth) / 2;
		int top = Math.max(0, (this.height - this.scaled(designHeight)) / 2);
		int fieldHeight = this.scaled(20);
		int gap = this.scaled(8);
		int halfWidth = (this.contentWidth - gap) / 2;
		int secondX = left + halfWidth + gap;

		this.addLabel("MOTD", left, top);

		this.motdField = this.addRenderableWidget(
			MultiLineEditBox.builder()
				.setX(left)
				.setY(top + this.scaled(14))
				.build(this.font, this.contentWidth, this.scaled(52), Component.literal("Message of the day"))
		);

		this.motdField.setCharacterLimit(256);
		this.motdField.setLineLimit(2);
		this.motdField.setValue(this.settings.motd);
		this.motdField.setValueListener(value -> this.settings.motd = java.util.Objects.requireNonNull(value));

		this.addLabel("Player Limit (1 Or More)", left, top + this.scaled(74));

		this.playerLimitField = this.addField(
			left, top + this.scaled(86), java.util.Objects.requireNonNull(Integer.toString(this.settings.maxPlayers)), 10, fieldHeight
		);

		this.addLabel("LAN Port (1-65535)", left, top + this.scaled(112));

		this.portField = this.addField(
			left, top + this.scaled(124), java.util.Objects.requireNonNull(Integer.toString(this.settings.port)), 5, fieldHeight
		);

		this.addLabel("Server Icon", left, top + this.scaled(153));
		this.previewSize = this.scaled(56);

		int iconGap = this.scaled(8);
		int iconControlWidth = this.contentWidth - this.previewSize - iconGap;
		int iconButtonHeight = this.scaled(20);

		this.previewX = left + this.contentWidth - this.previewSize;
		this.previewY = top + this.scaled(166);

		this.addRenderableWidget(
			Button.builder(Component.literal("Choose File"), button -> this.chooseIcon())
				.bounds(left, top + this.scaled(166), iconControlWidth, iconButtonHeight)
				.build()
		);

		this.addRenderableWidget(
			Button.builder(Component.literal("Clear Icon"), button -> this.clearIcon())
				.bounds(left, top + this.scaled(190), iconControlWidth, iconButtonHeight)
				.build()
		);

		int optionsY = top + this.scaled(230);

		this.addRenderableWidget(
			Button.builder(this.onlineModeLabel(), button -> {
				this.settings.onlineMode = !this.settings.onlineMode;
				button.setMessage(this.onlineModeLabel());
			}).bounds(left, optionsY, this.narrow ? this.contentWidth : halfWidth, fieldHeight).build()
		);

		this.addRenderableWidget(
			Button.builder(this.commandsLabel(), button -> {
				this.settings.allowCommands = !this.settings.allowCommands;
				button.setMessage(this.commandsLabel());
			}).bounds(
				this.narrow ? left : secondX,
				optionsY + (this.narrow ? this.scaled(24) : 0),

				this.narrow ? this.contentWidth : halfWidth,
				fieldHeight
			).build()
		);

		int saveY = top + this.scaled(this.narrow ? 280 : 258);
		int lanY = saveY + (this.narrow ? this.scaled(24) : 0);
		int backY = top + this.scaled(this.narrow ? 328 : 286);

		this.addRenderableWidget(
			Button.builder(Component.literal("Save Settings"), button -> this.saveAndApply())
				.bounds(left, saveY, this.narrow ? this.contentWidth : halfWidth, fieldHeight)
				.build()
		);

		this.lanButton = this.addRenderableWidget(
			Button.builder(this.lanButtonLabel(), button -> this.toggleLan())
				.bounds(this.narrow ? left : secondX, lanY, this.narrow ? this.contentWidth : halfWidth, fieldHeight)
				.build()
		);

		this.addRenderableWidget(
			Button.builder(Component.literal("Back"), button -> this.onClose())
				.bounds(this.width / 2 - this.scaled(50), backY, this.scaled(100), fieldHeight)
				.build()
		);

		this.statusLabel = this.addRenderableWidget(
			new StringWidget(left, top + this.scaled(this.narrow ? 354 : 310), this.contentWidth, this.scaled(12), Component.empty(), this.font)
		);

		this.refreshIconPreview();
	}

	private void addLabel(final @NonNull String label, final int x, final int y) {
		this.addRenderableWidget(new StringWidget(x, y, this.contentWidth, this.scaled(10), Component.literal(label), this.font));
	}

	private EditBox addField(final int x, final int y, final @NonNull String value, final int maxLength, final int height) {
		EditBox field = this.addRenderableWidget(
			new LanEditBox(this.font, x, y, this.contentWidth, height, Component.empty())
		);

		field.setMaxLength(maxLength);
		field.setValue(value);

		return field;
	}

	private int scaled(final int value) {
		return Math.max(1, Math.round(value * this.layoutScale));
	}

	private @NonNull Component onlineModeLabel() {
		return Component.literal("Online Mode: " + (this.settings.onlineMode ? "On" : "Off"));
	}

	private @NonNull Component commandsLabel() {
		return Component.literal("Commands: " + (this.settings.allowCommands ? "Enabled" : "Disabled"));
	}

	private @NonNull Component lanButtonLabel() {
		return Component.literal(this.server.isPublished() ? "Stop LAN" : "Open To LAN");
	}

	private void chooseIcon() {
		Path home = Path.of(System.getProperty("user.home", "."));

		this.minecraft.gui.setScreen(new LanIconPickerScreen(this, home, this::importIcon));
	}

	private void importIcon(final Path selected) {
		try {
			Path copiedIcon = LanWorldSettings.copyAndResizeIcon(this.server, selected);

			this.settings.iconPath = java.util.Objects.requireNonNull(
				java.util.Objects.requireNonNull(copiedIcon.getFileName()).toString()
			);

			LanWorldSettings.save(this.server, this.settings);
			LANStudio.applySettings(this.server, this.settings);

			this.refreshIconPreview();
			this.setStatus("Icon Set!");
		} catch (IOException | IllegalArgumentException exception) {
			LANStudio.LOGGER.error("Could Not Import LAN Server Icon {}", selected, exception);

			this.setStatus(this.errorMessage(exception, "Could Not Import The Selected PNG!"));
		}
	}

	private void clearIcon() {
		this.settings.iconPath = "";
		try {
			LanWorldSettings.save(this.server, this.settings);
			LANStudio.applySettings(this.server, this.settings);
			Path worldIcon = this.server.getWorldPath(LevelResource.ROOT).resolve("lanstudio-icon.png");
			Files.deleteIfExists(worldIcon);

			this.refreshIconPreview();
			this.setStatus("Custom Icon Cleared!");
		} catch (IOException exception) {
			LANStudio.LOGGER.error("Could Not Clear LAN Server Icon For World {}", this.server.getWorldData().getLevelName(), exception);

			this.setStatus(this.errorMessage(exception, "Could Not Clear The Custom Icon!"));
		}
	}

	private void refreshIconPreview() {
		Path iconPath = LanWorldSettings.resolveIconPath(this.server, this.settings);
		if (iconPath == null || !Files.isRegularFile(iconPath)) {
			this.iconPreviewLoaded = false;

			this.minecraft.getTextureManager().release(ICON_PREVIEW_TEXTURE);

			return;
		}

		try {
			NativeImage image;

			try (var input = java.util.Objects.requireNonNull(Files.newInputStream(iconPath))) {
				image = NativeImage.read(input);
			}

			this.minecraft.getTextureManager().register(
				ICON_PREVIEW_TEXTURE,
				new DynamicTexture(() -> "LAN Studio Icon Preview", image)
			);

			this.iconPreviewLoaded = true;
		} catch (IOException exception) {
			LANStudio.LOGGER.error("Could Not Load LAN Studio Icon Preview From {}", iconPath, exception);

			this.iconPreviewLoaded = false;

			this.minecraft.getTextureManager().release(ICON_PREVIEW_TEXTURE);
		}
	}

	@Override
	public void extractRenderState(final @NonNull GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.fill(this.previewX - 1, this.previewY - 1, this.previewX + this.previewSize + 1, this.previewY + this.previewSize + 1, 0xFF555555);

		if (this.iconPreviewLoaded) {
			graphics.blit(
				RenderPipelines.GUI_TEXTURED,
				ICON_PREVIEW_TEXTURE,
				this.previewX,
				this.previewY,
				0.0F,
				0.0F,
				this.previewSize,
				this.previewSize,
				64,
				64,
				64,
				64
			);
		} else {
			graphics.fill(this.previewX, this.previewY, this.previewX + this.previewSize, this.previewY + this.previewSize, 0xFF222222);
		}
	}

	private boolean saveAndApply() {
		try {
			int maxPlayers = Integer.parseInt(this.playerLimitField.getValue());
			int port = Integer.parseInt(this.portField.getValue());

			if (maxPlayers < 1) {
				throw new IllegalArgumentException("Player Limit Must Be At Least 1");
			}
			if (port < 1 || port > 65535) {
				throw new IllegalArgumentException("Port Must Be Between 1 And 65535");
			}

			this.settings.maxPlayers = maxPlayers;
			this.settings.port = port;
			this.settings.motd = this.motdField.getValue();

			LanWorldSettings.save(this.server, this.settings);
			LANStudio.applySettings(this.server, this.settings);

			this.setStatus("Settings Saved And Applied!");
			this.lanButton.setMessage(this.lanButtonLabel());

			return true;
		} catch (IOException | IllegalArgumentException exception) {
			LANStudio.LOGGER.error("Could Not Save Or Apply LAN Settings!", exception);

			this.setStatus(this.errorMessage(exception, "Could Not Save Settings! See The Log!"));

			return false;
		}
	}

	private void toggleLan() {
		if (!this.saveAndApply()) {
			return;
		}

		if (this.server.isPublished()) {
			if (this.server.unpublishServer()) {
				this.setStatus("LAN Sharing Stopped!");
			} else {
				this.setStatus("Could Not Stop LAN Sharing!");
			}
		} else if (this.server.publishServer(MinecraftServer.MultiplayerScope.LAN, this.settings.allowCommands, this.settings.port)) {
			this.setStatus("LAN Sharing Started On Port " + this.settings.port + "!");
		} else {
			this.setStatus("Could Not Open LAN Sharing On Port " + this.settings.port + "!");
		}

		this.lanButton.setMessage(this.lanButtonLabel());
	}

	private void setStatus(final @NonNull String message) {
		if (this.statusLabel != null) {
			this.statusLabel.setMessage(Component.literal(message));
		}
	}

	private @NonNull String errorMessage(final Exception exception, final @NonNull String fallback) {
		String message = exception.getMessage();

		return message == null ? fallback : java.util.Objects.requireNonNull(message);
	}

	@Override
	public void removed() {
		this.iconPreviewLoaded = false;

		this.minecraft.getTextureManager().release(ICON_PREVIEW_TEXTURE);

		super.removed();
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	private static final class LanEditBox extends EditBox {
		private LanEditBox(final net.minecraft.client.gui.Font font, final int x, final int y, final int width, final int height, final Component narration) {
			super(font, x, y, width, height, narration);
		}

		@Override
		public boolean keyPressed(final @NonNull KeyEvent event) {
			if (this.isActive() && this.isFocused() && event.isSelectAll()) {
				this.moveCursorToEnd(false);
				this.setHighlightPos(0);

				return true;
			}

			return super.keyPressed(event);
		}
	}
}
