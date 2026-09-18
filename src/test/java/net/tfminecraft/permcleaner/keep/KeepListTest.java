package net.tfminecraft.permcleaner.keep;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class KeepListTest {

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
	void keepsGroupMembership() {
		assertTrue(keep.keeps("group.default"));
	}

	@Test
	void keepsRpcharGroup() {
		assertTrue(keep.keeps("rpchar.group.noble"));
	}

	@Test
	void keepsArmourshopNodes() {
		assertTrue(keep.keeps("armourshop.submission.foo"));
		assertTrue(keep.keeps("armourshop"));
	}

	@Test
	void dropsProfessionNodes() {
		assertFalse(keep.keeps("professions.vegetables"));
	}

	@Test
	void keepsStaffFlags() {
		assertTrue(keep.keeps("tfmc.staff"));
		assertTrue(keep.keeps("tfmc.map.staff"));
	}

	@Test
	void doesNotTreatGroupsAsGroup() {
		assertFalse(keep.keeps("groups.admin"));
	}

	@Test
	void dropsRpcharProfessionUse() {
		assertFalse(keep.keeps("rpchar.profession.use"));
	}

	@Test
	void keepsRulequizCaseInsensitive() {
		assertTrue(keep.keeps("rulequiz.completed"));
		assertTrue(keep.keeps("RuleQuiz.Completed"));
	}

	@Test
	void blankAndNullAreNotKept() {
		assertFalse(keep.keeps(null));
		assertFalse(keep.keeps("  "));
	}
}
