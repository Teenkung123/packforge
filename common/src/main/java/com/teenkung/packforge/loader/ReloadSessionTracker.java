package com.teenkung.packforge.loader;

import com.teenkung.packforge.PackForge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public final class ReloadSessionTracker {
	private static final AtomicLong nextId = new AtomicLong();
	private static volatile PackDiff pendingDiff = PackDiff.empty();
	private static volatile ReloadSession current = new ReloadSession(0L, "unknown", List.of(), List.of());

	public static void capturePackDiff(Collection<String> oldPacks, Collection<String> newPacks) {
		List<String> oldList = oldPacks == null ? List.of() : List.copyOf(oldPacks);
		List<String> newList = newPacks == null ? List.of() : List.copyOf(newPacks);
		if (oldList.equals(newList)) {
			pendingDiff = PackDiff.empty();
			return;
		}
		pendingDiff = PackDiff.from(oldList, newList);
	}

	public static ReloadSession startReload() {
		PackDiff diff = pendingDiff;
		pendingDiff = PackDiff.empty();
		long id = nextId.incrementAndGet();
		String source = diff.hasChanges() ? "resource_pack_selection" : "manual_or_startup";
		ReloadSession session = new ReloadSession(id, source, diff.added(), diff.removed());
		current = session;
		PackForge.LOGGER.info("PackForge reload session: id={} source={} added={} removed={}",
			session.id(), session.source(), session.added(), session.removed());
		return session;
	}

	public static ReloadSession current() {
		return current;
	}

	private static List<String> difference(List<String> left, List<String> right) {
		Set<String> rightSet = new LinkedHashSet<>(right);
		List<String> result = new ArrayList<>();
		for (String item : left) {
			if (!rightSet.contains(item)) {
				result.add(item);
			}
		}
		return List.copyOf(result);
	}

	private ReloadSessionTracker() {}

	public record ReloadSession(long id, String source, List<String> added, List<String> removed) {
	}

	private record PackDiff(List<String> added, List<String> removed) {
		static PackDiff empty() {
			return new PackDiff(List.of(), List.of());
		}

		static PackDiff from(List<String> oldPacks, List<String> newPacks) {
			return new PackDiff(difference(newPacks, oldPacks), difference(oldPacks, newPacks));
		}

		boolean hasChanges() {
			return !this.added.isEmpty() || !this.removed.isEmpty();
		}
	}
}
