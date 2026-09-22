package net.tfminecraft.tlibs.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class TabCleanerTest {
	@Test
	void cleanTab_filtersCurrentArgumentNotRootCommand() {
		List<String> completions = new ArrayList<>(List.of("battlecreate", "battlestart", "opencvote"));
		TabCleaner.cleanTab(completions, new String[] {"warschedule", "1", "battle"});
		assertEquals(List.of("battlecreate", "battlestart"), completions);
	}

	@Test
	void cleanTab_filtersFirstSubcommandPrefix() {
		List<String> completions = new ArrayList<>(List.of("warschedule", "warstatus", "endwar"));
		TabCleaner.cleanTab(completions, new String[] {"war"});
		assertEquals(List.of("warschedule", "warstatus"), completions);
	}

	@Test
	void cleanTab_keepsAllWhenCurrentArgEmpty() {
		List<String> completions = new ArrayList<>(List.of("battlecreate", "battlestart"));
		TabCleaner.cleanTab(completions, new String[] {"warschedule", "1", ""});
		assertEquals(List.of("battlecreate", "battlestart"), completions);
	}

	@Test
	void cleanTab_withArgIndex_filtersSpecificToken() {
		List<String> completions = new ArrayList<>(List.of("battlecreate", "battlestart", "opencvote"));
		TabCleaner.cleanTab(completions, new String[] {"warschedule", "1", "battle"}, 2);
		assertEquals(List.of("battlecreate", "battlestart"), completions);
	}

	@Test
	void cleanTab_withArgIndex_filtersWarIdNotSubcommand() {
		List<String> completions = new ArrayList<>(List.of("1", "2", "10"));
		TabCleaner.cleanTab(completions, new String[] {"warschedule", "1"}, 1);
		assertEquals(List.of("1", "10"), completions);
	}
}
