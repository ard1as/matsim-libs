package org.matsim.contrib.accessibility.accMods;

import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.groups.ScoringConfigGroup;
import org.matsim.core.population.PersonUtils;

import java.util.OptionalDouble;

public class marginalUtilityOfMoneyMod implements pre_accModsInterface {

	private final double globalAvgIncome;
	private final double βm_default;

	public marginalUtilityOfMoneyMod(Population population, ScoringConfigGroup scoringConfigGroup) {
		this.globalAvgIncome = computeAvgIncome(population);
		this.βm_default = scoringConfigGroup.getScoringParameters(
			"default"
		).getMarginalUtilityOfMoney();
	}

	private static double computeAvgIncome(Population population) {
		OptionalDouble averageIncome = population.getPersons().values().stream()
			.map(PersonUtils::getIncome)     // may be null
			.filter(income -> income != null && income > 0)
			.mapToDouble(Double::doubleValue)
			.average();
		return averageIncome.getAsDouble();
	}

	// NEW: helper to get person-specific μ_m
	private double betaMoney(Person person) {
		Double personalIncome = PersonUtils.getIncome(person);
		if (personalIncome != null && personalIncome > 0) {
			return βm_default * (globalAvgIncome / personalIncome);
		}
		return βm_default;
	}

	@Override
	public double apply(Person person, double teleportTime_h, double teleportDist_m, double departureTime_h) {
		double marginalUtilityOfMoney = betaMoney(person);
		return marginalUtilityOfMoney; //todo rename this to what is used in calculator
	}
}
