package org.matsim.contrib.accessibility.accMods;

import org.matsim.api.core.v01.population.Person;
import org.matsim.core.population.PersonUtils;

public class age implements accessibilityModifierInterface {
	@Override
	public double apply(Person person, double teleportTime_h, double teleportDist_m, double departureTime_h, double contribution){
		int age = PersonUtils.getAge(person);
		if (age >= 60){
			return teleportTime_h * 1.5; // 50% penalty for elderly
		}
		else if (age >= 15 && age <30){
			return teleportTime_h * 0.9; // 10% bonus for young people
		}
		return teleportTime_h;
	}
}
