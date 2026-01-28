package org.reactome.release.downloaddirectory.StableIdMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 * Created 1/23/2026
 */
public class StableIdMapping {
	private List<String> stableIds;

	public StableIdMapping(List<String> stableIds) {
		this.stableIds = stableIds;
	}

	public String getPrimaryId() {
		return stableIds.get(0);
	}

	public List<String> getSecondaryIds() {
		List<String> stableIdsCopy = new ArrayList<>(stableIds);
		stableIdsCopy.remove(getPrimaryId());
		return stableIdsCopy;
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

	private boolean hasNewFormat(String stableId) {
		return stableId.matches("^R-.*");
	}
}
