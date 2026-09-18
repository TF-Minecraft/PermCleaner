package net.tfminecraft.permcleaner.lp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.PermissionNode;
import net.tfminecraft.permcleaner.keep.KeepList;
import net.tfminecraft.permcleaner.lp.CleanResult.NodeReport;

public final class UserPermissionCleaner {

	private UserPermissionCleaner() {}

	public static boolean shouldRemove(Node node, KeepList keep) {
		if (node == null || keep == null) {
			return false;
		}
		if (!(node instanceof PermissionNode permissionNode)) {
			return false;
		}
		return !keep.keeps(permissionNode.getPermission());
	}

	public static CleanResult inspect(User user, KeepList keep) {
		return classify(snapshot(user), keep, false, null);
	}

	public static CleanResult apply(User user, KeepList keep) {
		if (user == null) {
			return new CleanResult(0, 0, List.of());
		}
		return classify(snapshot(user), keep, true, user);
	}

	private static Collection<Node> snapshot(User user) {
		if (user == null) {
			return List.of();
		}
		return List.copyOf(user.data().toCollection());
	}

	private static CleanResult classify(Collection<Node> nodes, KeepList keep, boolean mutate, User user) {
		int removed = 0;
		int kept = 0;
		List<NodeReport> reports = new ArrayList<>();
		for (Node node : nodes) {
			boolean remove = shouldRemove(node, keep);
			reports.add(new NodeReport(
					node.getKey(),
					node.getClass().getSimpleName(),
					node.getContexts().toString(),
					remove));
			if (remove) {
				removed++;
				if (mutate && user != null) {
					user.data().remove(node);
				}
			} else {
				kept++;
			}
		}
		return new CleanResult(removed, kept, reports);
	}
}
