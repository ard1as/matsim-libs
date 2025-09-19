package org.matsim.contrib.accessibility.accMods;

import org.matsim.api.core.v01.population.Person;

public class departureTime implements post_accModsInterface {
	@Override
	public double apply(Person person, double teleportTime_h, double teleportDist_m, double departureTime_h, double contribution){
		if (departureTime_h < 6 || departureTime_h > 22){
			return contribution *  0.5; // places are closed during these times
		}
		return contribution;
	}
}
