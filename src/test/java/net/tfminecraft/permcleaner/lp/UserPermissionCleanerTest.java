package net.tfminecraft.permcleaner.lp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.luckperms.api.context.ImmutableContextSet;
import net.luckperms.api.model.data.NodeMap;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.MetaNode;
import net.luckperms.api.node.types.PermissionNode;
import net.tfminecraft.permcleaner.keep.KeepList;
import net.tfminecraft.permcleaner.lp.CleanResult.NodeReport;

class UserPermissionCleanerTest {

	private static KeepList keep;

	@BeforeAll
	static void loadProductionPatterns() {
		keep = KeepList.fromPatterns(List.of(
				"group.*",
				"rpchar.group.*",
				"armourshop.*",
				"rulequiz.completed",
				"tfmc.staff",
				"tfmc.map.staff"));
	}

	@Test
	void removesProfessionPermission() {
		PermissionNode node = mock(PermissionNode.class);
		when(node.getPermission()).thenReturn("professions.vegetables");
		assertTrue(UserPermissionCleaner.shouldRemove(node, keep));
	}

	@Test
	void keepsArmourshopPermission() {
		PermissionNode node = mock(PermissionNode.class);
		when(node.getPermission()).thenReturn("armourshop.submission.foo");
		assertFalse(UserPermissionCleaner.shouldRemove(node, keep));
	}

	@Test
	void keepsInheritance() {
		assertFalse(UserPermissionCleaner.shouldRemove(mock(InheritanceNode.class), keep));
	}

	@Test
	void keepsMeta() {
		assertFalse(UserPermissionCleaner.shouldRemove(mock(MetaNode.class), keep));
	}

	@Test
	void removesNegatedProfessionPermission() {
		PermissionNode node = mock(PermissionNode.class);
		when(node.getPermission()).thenReturn("professions.foo");
		when(node.getValue()).thenReturn(false);
		assertTrue(UserPermissionCleaner.shouldRemove(node, keep));
	}

	@Test
	void removesProfessionPermissionInServerContext() {
		PermissionNode node = mock(PermissionNode.class);
		when(node.getPermission()).thenReturn("professions.vegetables");
		assertTrue(UserPermissionCleaner.shouldRemove(node, keep));
	}

	@Test
	void keepsWhenNodeOrKeepListMissing() {
		PermissionNode node = mock(PermissionNode.class);
		when(node.getPermission()).thenReturn("professions.vegetables");
		assertFalse(UserPermissionCleaner.shouldRemove(null, keep));
		assertFalse(UserPermissionCleaner.shouldRemove(node, null));
	}

	@Test
	void inspectReportsWithoutRemoving() {
		PermissionNode profession = permission("professions.vegetables", "{server=lobby}");
		InheritanceNode group = inheritance("group.default");
		NodeMap data = nodes(profession, group);

		CleanResult result = UserPermissionCleaner.inspect(user(data), keep);

		assertEquals(1, result.removed());
		assertEquals(1, result.kept());
		List<NodeReport> reports = result.nodes();
		assertEquals(2, reports.size());
		assertEquals("professions.vegetables", reports.get(0).key());
		assertEquals("{server=lobby}", reports.get(0).context());
		assertEquals(profession.getClass().getSimpleName(), reports.get(0).type());
		assertTrue(reports.get(0).remove());
		assertEquals("group.default", reports.get(1).key());
		assertFalse(reports.get(1).remove());
		verify(data, never()).remove(any());
	}

	@Test
	void inspectWithoutUserIsEmpty() {
		CleanResult result = UserPermissionCleaner.inspect(null, keep);
		assertEquals(0, result.removed());
		assertEquals(0, result.kept());
		assertTrue(result.nodes().isEmpty());
	}

	@Test
	void applyRemovesOnlyUnkeptPermissions() {
		PermissionNode profession = permission("professions.vegetables", "{}");
		PermissionNode armourshop = permission("armourshop.submission.foo", "{}");
		InheritanceNode group = inheritance("group.default");
		NodeMap data = nodes(profession, armourshop, group);

		CleanResult result = UserPermissionCleaner.apply(user(data), keep);

		assertEquals(1, result.removed());
		assertEquals(2, result.kept());
		assertTrue(result.changed());
		verify(data).remove(profession);
		verify(data, never()).remove(armourshop);
		verify(data, never()).remove(group);
	}

	@Test
	void applyWithoutUserIsEmpty() {
		CleanResult result = UserPermissionCleaner.apply(null, keep);
		assertEquals(0, result.removed());
		assertEquals(0, result.kept());
		assertFalse(result.changed());
	}

	@Test
	void applyToUserWithoutNodesChangesNothing() {
		NodeMap data = nodes();
		CleanResult result = UserPermissionCleaner.apply(user(data), keep);
		assertFalse(result.changed());
		verify(data, never()).remove(any());
	}

	private static PermissionNode permission(String key, String context) {
		PermissionNode node = mock(PermissionNode.class);
		when(node.getKey()).thenReturn(key);
		when(node.getPermission()).thenReturn(key);
		ImmutableContextSet contexts = mock(ImmutableContextSet.class);
		when(contexts.toString()).thenReturn(context);
		when(node.getContexts()).thenReturn(contexts);
		return node;
	}

	private static InheritanceNode inheritance(String key) {
		InheritanceNode node = mock(InheritanceNode.class);
		when(node.getKey()).thenReturn(key);
		when(node.getContexts()).thenReturn(mock(ImmutableContextSet.class));
		return node;
	}

	private static NodeMap nodes(Node... nodes) {
		NodeMap data = mock(NodeMap.class);
		when(data.toCollection()).thenReturn(List.of(nodes));
		return data;
	}

	private static User user(NodeMap data) {
		User user = mock(User.class);
		when(user.data()).thenReturn(data);
		return user;
	}
}
