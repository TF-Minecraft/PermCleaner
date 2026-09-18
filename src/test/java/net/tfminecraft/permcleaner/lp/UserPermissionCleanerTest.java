package net.tfminecraft.permcleaner.lp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.MetaNode;
import net.luckperms.api.node.types.PermissionNode;
import net.tfminecraft.permcleaner.keep.KeepList;

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
}
