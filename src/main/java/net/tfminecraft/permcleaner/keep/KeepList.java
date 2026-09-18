package net.tfminecraft.permcleaner.keep;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class KeepList {

	private final List<Rule> rules;

	private KeepList(List<Rule> rules) {
		this.rules = List.copyOf(rules);
	}

	public static KeepList fromPatterns(List<String> patterns) {
		List<Rule> rules = new ArrayList<>();
		if (patterns == null) {
			return new KeepList(rules);
		}
		for (String pattern : patterns) {
			if (pattern == null || pattern.isBlank()) {
				continue;
			}
			String trimmed = pattern.trim();
			if (trimmed.endsWith(".*")) {
				String prefix = trimmed.substring(0, trimmed.length() - 2);
				if (prefix.isBlank() || prefix.contains("*")) {
					continue;
				}
				rules.add(Rule.prefix(prefix));
				continue;
			}
			if (trimmed.contains("*")) {
				continue;
			}
			rules.add(Rule.exact(trimmed));
		}
		return new KeepList(rules);
	}

	public boolean keeps(String node) {
		if (node == null || node.isBlank()) {
			return false;
		}
		String key = node.trim().toLowerCase(Locale.ROOT);
		for (Rule rule : rules) {
			if (rule.matches(key)) {
				return true;
			}
		}
		return false;
	}

	public boolean isEmpty() {
		return rules.isEmpty();
	}

	private static final class Rule {
		private final String value;
		private final boolean prefix;

		private Rule(String value, boolean prefix) {
			this.value = value.toLowerCase(Locale.ROOT);
			this.prefix = prefix;
		}

		static Rule exact(String value) {
			return new Rule(value, false);
		}

		static Rule prefix(String value) {
			return new Rule(value, true);
		}

		boolean matches(String node) {
			if (!prefix) {
				return node.equals(value);
			}
			return node.equals(value) || node.startsWith(value + ".");
		}
	}
}
