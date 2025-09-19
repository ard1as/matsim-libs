package org.matsim.contrib.accessibility.accMods;

import org.matsim.api.core.v01.population.Person;

public interface post_accModsInterface {
	/**
	 * Applies a penalty (or bonus) to the input variables and/or contribution of an opportunity.
	 *
	 * @param person MATSim agent
	 * @param teleportTime_h travel time (modifiable)
	 * @param teleportDist_m travel distance (not always needed but available)
	 * @param departureTime_h departure time in hours
	 * @param contribution current contribution (already computed from utility)
	 * @return adjusted contribution (after penalty/bonus)
	 */
	double apply(Person person, double teleportTime_h, double teleportDist_m, double departureTime_h, double contribution);
}
