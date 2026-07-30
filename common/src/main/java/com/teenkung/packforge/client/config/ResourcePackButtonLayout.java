package com.teenkung.packforge.client.config;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Finds a safe location for a small action button on the resource-pack screen.
 *
 * <p>This class deliberately uses only simple rectangles so every Minecraft adapter can
 * translate its own widget types without coupling the placement policy to a GUI API.</p>
 */
public final class ResourcePackButtonLayout {
	private ResourcePackButtonLayout() {
	}

	/**
	 * Finds a placement in this order: after the bottom-right action-button cluster, before
	 * the mirrored bottom-left cluster, then on the row directly above the right cluster.
	 */
	public static Optional<Rectangle> findPlacement(
		Rectangle screenBounds,
		ActionButtonAnchors anchors,
		int buttonWidth,
		int buttonHeight,
		int anchorGap,
		int buttonGap,
		List<Rectangle> occupiedRectangles
	) {
		Objects.requireNonNull(screenBounds, "screenBounds");
		Objects.requireNonNull(anchors, "anchors");
		Objects.requireNonNull(occupiedRectangles, "occupiedRectangles");
		if (buttonWidth <= 0 || buttonHeight <= 0 || anchorGap < 0 || buttonGap < 0) {
			throw new IllegalArgumentException("Button dimensions must be positive and gaps cannot be negative.");
		}

		List<Rectangle> occupied = List.copyOf(occupiedRectangles);
		Rectangle right = moveRight(
			screenBounds,
			new Rectangle(anchors.doneButton().right() + anchorGap, anchors.doneButton().y(), buttonWidth, buttonHeight),
			buttonGap,
			occupied
		);
		if (right != null) {
			return Optional.of(right);
		}

		Rectangle left = moveLeft(
			screenBounds,
			new Rectangle(anchors.openPackFolderButton().x() - anchorGap - buttonWidth, anchors.openPackFolderButton().y(), buttonWidth, buttonHeight),
			buttonGap,
			occupied
		);
		if (left != null) {
			return Optional.of(left);
		}

		Rectangle above = moveRight(
			screenBounds,
			new Rectangle(anchors.doneButton().right() + anchorGap, anchors.doneButton().y() - buttonGap - buttonHeight, buttonWidth, buttonHeight),
			buttonGap,
			occupied
		);
		return Optional.ofNullable(above);
	}

	private static Rectangle moveRight(Rectangle screenBounds, Rectangle candidate, int gap, List<Rectangle> occupied) {
		while (screenBounds.contains(candidate)) {
			Rectangle collision = firstCollision(candidate, occupied);
			if (collision == null) {
				return candidate;
			}
			candidate = new Rectangle(collision.right() + gap, candidate.y(), candidate.width(), candidate.height());
		}
		return null;
	}

	private static Rectangle moveLeft(Rectangle screenBounds, Rectangle candidate, int gap, List<Rectangle> occupied) {
		while (screenBounds.contains(candidate)) {
			Rectangle collision = firstCollision(candidate, occupied);
			if (collision == null) {
				return candidate;
			}
			candidate = new Rectangle(collision.x() - gap - candidate.width(), candidate.y(), candidate.width(), candidate.height());
		}
		return null;
	}

	private static Rectangle firstCollision(Rectangle candidate, List<Rectangle> occupied) {
		return occupied.stream()
			.filter(candidate::overlaps)
			.min(java.util.Comparator.comparingInt(Rectangle::x))
			.orElse(null);
	}

	/** Rectangles use the same exclusive right/bottom edges as Minecraft widgets. */
	public record Rectangle(int x, int y, int width, int height) {
		public Rectangle {
			if (width < 0 || height < 0) {
				throw new IllegalArgumentException("Rectangle dimensions cannot be negative.");
			}
		}

		public int right() {
			return x + width;
		}

		public int bottom() {
			return y + height;
		}

		public boolean contains(Rectangle other) {
			return other.x >= x && other.y >= y && other.right() <= right() && other.bottom() <= bottom();
		}

		public boolean overlaps(Rectangle other) {
			return x < other.right() && right() > other.x && y < other.bottom() && bottom() > other.y;
		}
	}

	/** The existing vanilla buttons used as stable placement anchors. */
	public record ActionButtonAnchors(Rectangle openPackFolderButton, Rectangle doneButton) {
		public ActionButtonAnchors {
			Objects.requireNonNull(openPackFolderButton, "openPackFolderButton");
			Objects.requireNonNull(doneButton, "doneButton");
		}
	}
}
