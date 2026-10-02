package net.tfminecraft.permcleaner.lp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.tfminecraft.permcleaner.lp.CleanResult.NodeReport;

class CleanResultTest {

	@Test
	void unchangedWhenNothingRemoved() {
		CleanResult result = new CleanResult(0, 3, List.of());
		assertEquals(0, result.removed());
		assertEquals(3, result.kept());
		assertFalse(result.changed());
	}

	@Test
	void changedWhenNodesRemoved() {
		assertTrue(new CleanResult(2, 0, List.of()).changed());
	}

	@Test
	void copiesNodeReports() {
		List<NodeReport> source = new ArrayList<>();
		source.add(new NodeReport("professions.foo", "PermissionNode", "{}", true));
		CleanResult result = new CleanResult(1, 0, source);
		source.clear();

		assertEquals(1, result.nodes().size());
		assertThrows(UnsupportedOperationException.class, () -> result.nodes().clear());
	}

	@Test
	void nodeReportKeepsValues() {
		NodeReport report = new NodeReport("group.default", "InheritanceNode", "{server=lobby}", false);
		assertEquals("group.default", report.key());
		assertEquals("InheritanceNode", report.type());
		assertEquals("{server=lobby}", report.context());
		assertFalse(report.remove());
	}

	@Test
	void nodeReportReplacesNullsWithBlanks() {
		NodeReport report = new NodeReport(null, null, null, true);
		assertEquals("", report.key());
		assertEquals("", report.type());
		assertEquals("", report.context());
		assertTrue(report.remove());
	}
}
