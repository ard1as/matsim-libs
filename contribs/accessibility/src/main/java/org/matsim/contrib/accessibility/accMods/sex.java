package org.matsim.contrib.accessibility.accMods;

import org.matsim.api.core.v01.population.Person;

public class sex implements accessibilityModifierInterface {
	@Override
	public double apply(Person person, double teleportTime_h, double teleportDist_m, double departureTime_h, double contribution){
		Object sex = person.getAttributes().getAttribute("sex");
		if (sex != null && sex.equals("f") && departureTime_h < 6 || departureTime_h > 22) {
			return contribution * 0.8;
		}
		return contribution;
	}
}
