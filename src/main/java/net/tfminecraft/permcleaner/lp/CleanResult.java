package net.tfminecraft.permcleaner.lp;

import java.util.List;
import java.util.Objects;

public final class CleanResult {

	private final int removed;
	private final int kept;
	private final List<NodeReport> nodes;

	public CleanResult(int removed, int kept, List<NodeReport> nodes) {
		this.removed = removed;
		this.kept = kept;
		this.nodes = List.copyOf(nodes);
	}

	public int removed() {
		return removed;
	}

	public int kept() {
		return kept;
	}

	public List<NodeReport> nodes() {
		return nodes;
	}

	public boolean changed() {
		return removed > 0;
	}

	public static final class NodeReport {
		private final String key;
		private final String type;
		private final String context;
		private final boolean remove;

		public NodeReport(String key, String type, String context, boolean remove) {
			this.key = Objects.requireNonNullElse(key, "");
			this.type = Objects.requireNonNullElse(type, "");
			this.context = Objects.requireNonNullElse(context, "");
			this.remove = remove;
		}

		public String key() {
			return key;
		}

		public String type() {
			return type;
		}

		public String context() {
			return context;
		}

		public boolean remove() {
			return remove;
		}
	}
}
