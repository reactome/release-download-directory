package org.reactome.release.downloaddirectory.StableIdMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 * Created 1/23/2026
 */
public class StableIdMapping {
	private final static Pattern STABLE_ID_PATTERN = Pattern.compile("^R-(.{3})-.*");

	private final List<String> stableIds;
	private List<String> secondaryIds;

	public StableIdMapping(List<String> stableIds) {
		if (stableIds == null || stableIds.isEmpty()) {
			throw new IllegalArgumentException("stableIds can not be null or empty");
		}

		this.stableIds = new ArrayList<>(stableIds);
	}

	public String getPrimaryId() {
		return stableIds.get(0);
	}

	public List<String> getSecondaryIds() {
		if (this.secondaryIds == null) {
			List<String> stableIdsCopy = new ArrayList<>(stableIds);
			stableIdsCopy.remove(getPrimaryId());
			this.secondaryIds = filterOutMismatchedIdentifiers(stableIdsCopy);
		}
		return this.secondaryIds;
	}

	public boolean hasNewFormatPrimaryId() {
		return hasNewFormat(getPrimaryId());
	}

	public boolean hasSecondaryIds() {
		return !getSecondaryIds().isEmpty();
	}

	public boolean isHuman() {
		return getPrimaryId().matches("R-HSA.*");
	}

	@Override
	public String toString() {
		return getPrimaryId() + "\t" + String.join(",", getSecondaryIds()) + "\n";
	}

	private List<String> filterOutMismatchedIdentifiers(List<String> stableIds) {
		List<String> filteredStableIds = new ArrayList<>();
		for (String stableId : stableIds) {
			if (stableId.startsWith("REACT_") ||
				getAbbreviation(getPrimaryId()).equals(getAbbreviation(stableId)) ||
				getAbbreviationExceptions().contains(getAbbreviation(stableId)) ||
				(getAbbreviationExceptions().contains(getPrimaryId()) && noMismatchWithAlreadyFilteredStableIds(stableId, filteredStableIds))
			) {
				filteredStableIds.add(stableId);
			}
		}
		return filteredStableIds;
	}

	private boolean noMismatchWithAlreadyFilteredStableIds(String stableId, List<String> filteredStableIds) {
		if (getAbbreviationExceptions().contains(getAbbreviation(stableId))) {
			return true;
		}
		for (String filteredStableId : filteredStableIds) {
			if (!getAbbreviation(stableId).equals(getAbbreviation(filteredStableId))) {
				return false;
			}
		}
		return true;
	}

	private boolean hasNewFormat(String stableId) {
		return stableId.matches("^R-.*");
	}

	private String getAbbreviation(String stableId) {
		Matcher stableIdMatcher = STABLE_ID_PATTERN.matcher(stableId);

		if (stableIdMatcher.find()) {
			return stableIdMatcher.group(1);
		} else {
			return "";
		}
	}

	private List<String> getAbbreviationExceptions() {
		return Arrays.asList("ALL", "NUL", "HC ", "HCV", "HPC", "HPB", "HBV");
	}
}
