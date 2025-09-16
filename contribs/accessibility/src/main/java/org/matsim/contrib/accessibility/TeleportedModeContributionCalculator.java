package org.matsim.contrib.accessibility;

import org.matsim.api.core.v01.BasicLocation;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.accessibility.accMods.accessibilityModifierInterface;
import org.matsim.contrib.accessibility.accMods.economic_status;
import org.matsim.contrib.accessibility.utils.AggregationObject;
import org.matsim.core.config.groups.ScoringConfigGroup;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.router.TripRouter;
import org.matsim.facilities.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.matsim.contrib.accessibility.AccessibilityUtils.extractLeg;

public class TeleportedModeContributionCalculator implements AccessibilityContributionCalculator {


	private final String mode;
	private final double betaTT_h;
	private final double betaDist_m;
	private final double asc;
	TripRouter tripRouter;
	private Map<Id<? extends BasicLocation>, ArrayList<ActivityFacility>> aggregatedMeasurePoints;
	private Map<Id<? extends BasicLocation>, AggregationObject> aggregatedOpportunities;
	private ScoringConfigGroup scoringConfigGroup;

	private final List<accessibilityModifierInterface> accMods = new ArrayList<>(); //todo for test accMods interface

	TeleportedModeContributionCalculator(String mode, TripRouter tripRouter, ScoringConfigGroup scoringConfigGroup) {

		this.tripRouter = tripRouter;
		this.mode = mode;
		this.scoringConfigGroup = scoringConfigGroup;

		this.betaTT_h = scoringConfigGroup.getModes().get(mode).getMarginalUtilityOfTraveling() - scoringConfigGroup.getPerforming_utils_hr();
		this.betaDist_m = scoringConfigGroup.getModes().get(mode).getMarginalUtilityOfDistance();
		this.asc = scoringConfigGroup.getModes().get(mode).getConstant();

		accMods.add(new economic_status()); //todo register accMods here

	}

	@Override
	public void initialize(ActivityFacilities measuringPoints, ActivityFacilities opportunities) {
		this.aggregatedMeasurePoints = new ConcurrentHashMap<>();
		for (ActivityFacility measuringPoint : measuringPoints.getFacilities().values()) {
			Id<ActivityFacility> facilityId = measuringPoint.getId();
			if (!aggregatedMeasurePoints.containsKey(facilityId)) {
				aggregatedMeasurePoints.put(facilityId, new ArrayList<>());
			}
			aggregatedMeasurePoints.get(facilityId).add(measuringPoint);
		}


		this.aggregatedOpportunities = new ConcurrentHashMap<>();
		for (ActivityFacility opportunity : opportunities.getFacilities().values()) {
			AggregationObject opportunityAsAggObj = new AggregationObject(opportunity.getId(), null, null, opportunity, 0.);
			aggregatedOpportunities.put(opportunity.getId(), opportunityAsAggObj);
		}
	}

	@Override
	public void notifyNewOriginNode(Id<? extends BasicLocation> fromNodeId, Double departureTime) {
		//		nothing to do here...
	}

	@Override
	public double computeContributionOfOpportunity(ActivityFacility origin, Map<Id<? extends BasicLocation>, AggregationObject> aggregatedOpportunities, Double departureTime) {
		// initialize sum of utilities
		double expSum = 0.;

		for (AggregationObject destination : aggregatedOpportunities.values()) {

			Facility opportunity = (Facility) destination.getNearestBasicLocation();

			List<? extends PlanElement> planElements = tripRouter.calcRoute(mode, origin, opportunity, departureTime, null, null);
			Leg walkLeg = extractLeg(planElements, mode);
			double teleportTime_h = walkLeg.getTravelTime().seconds() / 3600;
			double teleportDist_m = walkLeg.getRoute().getDistance();
			double utilityTeleport = teleportTime_h * betaTT_h + teleportDist_m * betaDist_m + asc;
			expSum += Math.exp(this.scoringConfigGroup.getBrainExpBeta() * utilityTeleport);
		}

		return expSum;


	}

	public double computeContributionOfOpportunityPerson(Person person, Map<Id<? extends BasicLocation>, AggregationObject> aggregatedOpportunities, Double departureTime) {

		double expSum = 0.;

		for (AggregationObject destination : aggregatedOpportunities.values()) {

			Facility opportunity = (Facility) destination.getNearestBasicLocation();

			Facility homeFacility = FacilitiesUtils.wrapActivity((Activity) person.getSelectedPlan().getPlanElements().get(0));
			List<? extends PlanElement> planElements = tripRouter.calcRoute(mode, homeFacility, opportunity, departureTime, null, null);
			Leg walkLeg = extractLeg(planElements, mode);
			double teleportTime_h = walkLeg.getTravelTime().seconds() / 3600;
			double teleportDist_m = walkLeg.getRoute().getDistance();
			double departureTime_h = walkLeg.getDepartureTime().seconds() / 3600; //todo added departureTime_h

			//todo penalty for old age (60+)
			if (PersonUtils.getAge(person) >= 60){
				teleportTime_h *= 1.5; // walking time takes 1.5x as long for older person
			}
			/*if(PersonUtils.getAge(person) > 60) {
				utilityTeleport -= 10.0;
			}*/

			//todo bonus for young age (15-29)
			if (PersonUtils.getAge(person) >= 15 && PersonUtils.getAge(person) < 30){
				teleportTime_h *= 0.9; // slightly faster walkers
			}

			//todo penalty for restricted_mobility



			//todo base utility
			double utilityTeleport = teleportTime_h * betaTT_h + teleportDist_m * betaDist_m + asc; // here can play around with weights
			//	betaTT_h	= marginal utility of travel time (-)
			//	betaDist_m	= marginal utility of distance (-)
			//	asc			= mode specific constant

			/*penalty for low economic_status
			Object economic_status = person.getAttributes().getAttribute("economic_status");
			if(economic_status != null && economic_status.equals("low")){
				utilityTeleport -= 5.0;
			}*/

			//todo per-opportunity accessibility score
			double contribution = Math.exp(this.scoringConfigGroup.getBrainExpBeta() * utilityTeleport);

			//todo test applying mods using interface*
			for (accessibilityModifierInterface mods : accMods){
				contribution = mods.apply(person, teleportTime_h, teleportDist_m, departureTime_h, contribution);
			}
			/*
			//todo low economic_status penalty (scaling opportunity contributions)
			Object economic_status = person.getAttributes().getAttribute("economic_status");
			if (economic_status != null && economic_status.equals("low")){
				contribution *= 0.8;	// low income person has less access to/doesnt consider all available opportunities/facilities
			}
			*/


			//todo departureTime penalty (very early/late less opportunity contributions)
			if (departureTime_h < 6 || departureTime_h > 22){
				contribution *=  0.5; // places are closed during these times

			}

			//todo departureTime penalty for women (perceived lower safety walking at  night)
			Object sex = person.getAttributes().getAttribute("sex");
			if (sex != null && sex.equals("f") && departureTime_h < 6 || departureTime_h > 22){
				contribution *= 0.8;
			}

			//todo total accessibility including economic_status penalty
			expSum += contribution;
		}

		return expSum;
	}

	@Override
	public Map<Id<? extends BasicLocation>, ArrayList<ActivityFacility>> getAggregatedMeasurePoints() {
		return aggregatedMeasurePoints;
	}

	@Override
	public Map<Id<? extends BasicLocation>, AggregationObject> getAgregatedOpportunities() {
		return aggregatedOpportunities;
	}

	@Override
	public AccessibilityContributionCalculator duplicate() {
		TeleportedModeContributionCalculator copy = new TeleportedModeContributionCalculator(mode, tripRouter, scoringConfigGroup);
		copy.aggregatedOpportunities = this.aggregatedOpportunities;
		copy.aggregatedMeasurePoints = this.aggregatedMeasurePoints;
		return copy;
	}

}
