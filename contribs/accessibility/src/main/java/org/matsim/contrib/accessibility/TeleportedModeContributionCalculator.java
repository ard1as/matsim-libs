package org.matsim.contrib.accessibility;

import org.matsim.api.core.v01.BasicLocation;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.accessibility.accMods.*;
import org.matsim.contrib.accessibility.utils.AggregationObject;
import org.matsim.contrib.accessibility.utils.NetworkUtil;
import org.matsim.core.config.groups.ScoringConfigGroup;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.router.TripRouter;
import org.matsim.core.scenario.ScenarioUtils;
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

	//todo for test accMods interface (pre + post)
	private final List<pre_accModsInterface> pre_accMods = new ArrayList<>();
	private final List<post_accModsInterface> post_accMods = new ArrayList<>();

	TeleportedModeContributionCalculator(String mode, TripRouter tripRouter, ScoringConfigGroup scoringConfigGroup) {

		this.tripRouter = tripRouter;
		this.mode = mode;
		this.scoringConfigGroup = scoringConfigGroup;

		this.betaTT_h = scoringConfigGroup.getModes().get(mode).getMarginalUtilityOfTraveling() - scoringConfigGroup.getPerforming_utils_hr();
		this.betaDist_m = scoringConfigGroup.getModes().get(mode).getMarginalUtilityOfDistance();
		this.asc = scoringConfigGroup.getModes().get(mode).getConstant();

		//todo register accMods here
		pre_accMods.add(new age());
//		post_accMods.add(new economic_status());
//		post_accMods.add(new sex());

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
			//todo apply pre accMods
			for (pre_accModsInterface mods : pre_accMods){
				teleportTime_h = mods.apply((Person) origin.getAttributes().getAttribute("person"), teleportTime_h, teleportDist_m);
			}
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

			//todo applying pre accMods using interface (age)
			for (pre_accModsInterface mods : pre_accMods){
				teleportTime_h = mods.apply(person, teleportTime_h, teleportDist_m);
			}

			//test push
			//todo base utility (for other modes, add betaMoney etc.)
			double utilityTeleport = teleportTime_h * betaTT_h + teleportDist_m * betaDist_m + asc; // here can play around with weights
			//	betaTT_h	= marginal utility of travel time (-)
			//	betaDist_m	= marginal utility of distance (-)
			//	asc			= mode specific constant

			//todo per-opportunity accessibility score
			double contribution = Math.exp(this.scoringConfigGroup.getBrainExpBeta() * utilityTeleport);

			//todo applying post accMods using interface (economic_status, sex)
//			for (post_accModsInterface mods : post_accMods){
//				contribution = mods.apply(person, teleportTime_h, teleportDist_m, departureTime_h, contribution);
//			}

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
