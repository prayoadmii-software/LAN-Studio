package git.prayoadmii.lanstudio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

public final class LanIconPickerScreen extends Screen {
	private static final int PAGE_SIZE = 6;
	private static final int DESIGN_WIDTH = 440;
	private static final int DESIGN_HEIGHT = 282;
	private final Screen parent;
	private final Consumer<Path> onSelected;
	private Path directory;
	private List<Path> entries = List.of();
	private int page;
	private float scale;
	private int contentWidth;
	private EditBox pathField;
	private StringWidget statusLabel;

	public LanIconPickerScreen(final Screen parent, final Path initialDirectory, final Consumer<Path> onSelected) {
		super(Component.literal("Choose LAN Server Icon"));
		this.parent = parent;
		this.directory = initialDirectory;
		this.onSelected = onSelected;
	}

	@Override
	protected void init() {
		this.scale = Math.min(1.0F, Math.min(Math.max(0, this.width - 20) / (float)DESIGN_WIDTH, Math.max(0, this.height - 16) / (float)DESIGN_HEIGHT));
		this.contentWidth = Math.round(DESIGN_WIDTH * this.scale);
		this.refreshEntries();
	}

	private void refreshEntries() {
		try (var children = Files.list(this.directory)) {
			this.entries = children
				.filter(path -> Files.isDirectory(path) || path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".png"))
				.sorted(
					Comparator.<Path, Boolean>comparing(path -> !Files.isDirectory(path))
						.thenComparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER)
				)
				.toList();
			this.page = Math.min(this.page, Math.max(0, (this.entries.size() - 1) / PAGE_SIZE));
			this.rebuildWidgets("");
		} catch (IOException | SecurityException exception) {
			LANStudio.LOGGER.error("Could not list files in LAN icon picker directory {}", this.directory, exception);
			this.entries = List.of();
			this.rebuildWidgets("Could not read this folder; try another directory.");
		}
	}

	private void rebuildWidgets(final String status) {
		@NonNull String currentPath = this.pathField == null
			? java.util.Objects.requireNonNull(this.directory.toString())
			: java.util.Objects.requireNonNull(this.pathField.getValue());
		this.clearWidgets();
		int left = (this.width - this.contentWidth) / 2;
		int top = Math.max(0, (this.height - this.scaled(DESIGN_HEIGHT)) / 2);
		int rowHeight = this.scaled(22);
		int buttonHeight = this.scaled(20);
		int goWidth = this.scaled(52);
		int gap = this.scaled(6);

		this.addRenderableWidget(
			new StringWidget(left, top, this.contentWidth, this.scaled(12), Component.literal("Choose a PNG image"), this.font)
		);
		this.pathField = this.addRenderableWidget(
			new EditBox(this.font, left, top + this.scaled(16), this.contentWidth - goWidth - gap, buttonHeight, Component.literal("Folder path"))
		);
		this.pathField.setMaxLength(1024);
		this.pathField.setValue(currentPath);
		this.addRenderableWidget(
			Button.builder(Component.literal("Go"), button -> this.navigateToPath())
				.bounds(left + this.contentWidth - goWidth, top + this.scaled(16), goWidth, buttonHeight)
				.build()
		);
		this.addRenderableWidget(
			Button.builder(Component.literal("Up"), button -> this.navigateToParent())
				.bounds(left, top + this.scaled(42), this.scaled(64), buttonHeight)
				.build()
		);

		int start = this.page * PAGE_SIZE;
		int end = Math.min(this.entries.size(), start + PAGE_SIZE);
		for (int i = start; i < end; i++) {
			Path entry = this.entries.get(i);
			boolean isDirectory = Files.isDirectory(entry);
			String label = entry.getFileName().toString() + (isDirectory ? "/" : "");
			int rowY = top + this.scaled(70) + (i - start) * rowHeight;
			this.addRenderableWidget(
				Button.builder(Component.literal(label), button -> {
					if (isDirectory) {
						this.directory = entry;
						this.page = 0;
						this.pathField = null;
						this.refreshEntries();
					} else {
						this.minecraft.gui.setScreen(this.parent);
						this.onSelected.accept(entry);
					}
				}).bounds(left, rowY, this.contentWidth, buttonHeight).build()
			);
		}

		int pagingY = top + this.scaled(70 + PAGE_SIZE * 22);
		Button previous = this.addRenderableWidget(
			Button.builder(Component.literal("Previous"), button -> {
				this.page--;
				this.rebuildWidgets("");
			}).bounds(left, pagingY, this.scaled(100), buttonHeight).build()
		);
		previous.active = this.page > 0;
		Button next = this.addRenderableWidget(
			Button.builder(Component.literal("Next"), button -> {
				this.page++;
				this.rebuildWidgets("");
			}).bounds(left + this.contentWidth - this.scaled(100), pagingY, this.scaled(100), buttonHeight).build()
		);
		next.active = end < this.entries.size();
		this.addRenderableWidget(
			Button.builder(Component.literal("Cancel"), button -> this.onClose())
				.bounds(this.width / 2 - this.scaled(50), top + this.scaled(236), this.scaled(100), buttonHeight)
				.build()
		);
		String message = status.isEmpty() && this.entries.isEmpty() ? "No PNG files or folders in this directory." : status;
		this.statusLabel = this.addRenderableWidget(
			new StringWidget(left, top + this.scaled(262), this.contentWidth, this.scaled(12), Component.literal(message), this.font)
		);
	}

	private void navigateToPath() {
		try {
			Path enteredPath = Path.of(this.pathField.getValue());
			Path target = enteredPath.isAbsolute() ? enteredPath : this.directory.resolve(enteredPath);
			if (!Files.isDirectory(target)) {
				this.statusLabel.setMessage(Component.literal("That folder does not exist or cannot be opened."));
				return;
			}

			this.directory = target.normalize();
			this.page = 0;
			this.pathField = null;
			this.refreshEntries();
		} catch (InvalidPathException exception) {
			this.statusLabel.setMessage(Component.literal("That folder path is invalid."));
		}
	}

	private void navigateToParent() {
		Path parentDirectory = this.directory.getParent();
		if (parentDirectory != null) {
			this.directory = parentDirectory;
			this.page = 0;
			this.pathField = null;
			this.refreshEntries();
		}
	}

	private int scaled(final int value) {
		return Math.max(1, Math.round(value * this.scale));
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}
}
