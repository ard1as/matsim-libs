package org.matsim.contrib.accessibility.accMods;

import org.matsim.api.core.v01.population.Person;

public class economic_statusCar implements post_accModsInterface {
	@Override
	public double apply(Person person, double teleportTime_h, double teleportDist_m, double departureTime_h, double contribution){
		Object economic_status = person.getAttributes().getAttribute("economic_status");
		if (economic_status != null && economic_status.equals("low")){
			return contribution * 0.8;	// low income person has less access to/doesnt consider all available opportunities/facilities
		}
		return contribution;
	}
}
