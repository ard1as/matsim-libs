package org.matsim.contrib.accessibility;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.BasicLocation;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.accessibility.accMods.*;
import org.matsim.contrib.accessibility.utils.*;
import org.matsim.contrib.roadpricing.RoadPricingScheme;
import org.matsim.core.config.groups.NetworkConfigGroup;
import org.matsim.core.config.groups.ScoringConfigGroup;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.router.TripRouter;
import org.matsim.core.router.costcalculators.TravelDisutilityFactory;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.facilities.*;
import org.matsim.utils.leastcostpathtree.LeastCostPathTree;

import java.util.*;

import static org.matsim.contrib.accessibility.AccessibilityUtils.extractLeg;

/**
 * @author thibautd, dziemke
 */
final class NetworkModeAccessibilityExpContributionCalculator implements AccessibilityContributionCalculator {
	private static final Logger LOG = LogManager.getLogger( NetworkModeAccessibilityExpContributionCalculator.class );

	private final String mode;
	private final TravelDisutilityFactory travelDisutilityFactory;
	private final TravelTime travelTime;
	private final Scenario scenario;

	private final TravelDisutility travelDisutility;
	private final ScoringConfigGroup scoringConfigGroup;
	private final NetworkConfigGroup networkConfigGroup;
	private final double betaCarTT_h;
	private final double betaCarDist_m;
	private final double betaCarDistMonetary_m;
	private final double marginalUtilityOfMoney;
	private final double globalAverageIncome;

	private Network subNetwork;

	private double betaWalkTT;
	private double walkSpeed_m_s;

	TripRouter tripRouter ;

	private Node fromNode = null;
	private LeastCostPathTree lcpt;
	//private final DijkstraTree dijkstraTree;
	//private final MultiNodePathCalculator multiNodePathCalculator;
	//private ImaginaryNode aggregatedToNodes;

	private Map<Id<? extends BasicLocation>, ArrayList<ActivityFacility>> aggregatedMeasurePoints;
	private Map<Id<? extends BasicLocation>, AggregationObject> aggregatedOpportunities;

	private final List<pre_accModsInterface> pre_accMods = new ArrayList<>();
	private final List<post_accModsInterface> post_accMods = new ArrayList<>();




	public NetworkModeAccessibilityExpContributionCalculator(String mode, final TravelTime travelTime, final TravelDisutilityFactory travelDisutilityFactory, TripRouter tripRouter, Scenario scenario) {
		this.mode = mode;
		this.travelTime = travelTime;
		this.travelDisutilityFactory = travelDisutilityFactory;
		this.scenario = scenario;

		this.tripRouter = tripRouter;
		Gbl.assertNotNull(travelDisutilityFactory);
		this.travelDisutility = travelDisutilityFactory.createTravelDisutility(travelTime);

		scoringConfigGroup = scenario.getConfig().scoring();
		networkConfigGroup = scenario.getConfig().network();

		RoadPricingScheme scheme = (RoadPricingScheme) scenario.getScenarioElement( RoadPricingScheme.ELEMENT_NAME );
//		this.lcpt = new LeastCostPathTreeExtended(travelTime, travelDisutility, scheme);
		this.lcpt = new LeastCostPathTree(travelTime, travelDisutility);
		//this.dijkstraTree = new DijkstraTree(network, travelDisutility, travelTime);
		//FastMultiNodeDijkstraFactory fastMultiNodeDijkstraFactory = new FastMultiNodeDijkstraFactory(true);
		//this.multiNodePathCalculator = (MultiNodePathCalculator) fastMultiNodeDijkstraFactory.createPathCalculator(network, travelDisutility, travelTime);

		betaWalkTT = scoringConfigGroup.getModes().get(TransportMode.walk).getMarginalUtilityOfTraveling() - scoringConfigGroup.getPerforming_utils_hr();

		this.walkSpeed_m_s = scenario.getConfig().routing().getTeleportedModeSpeeds().get(TransportMode.walk);
		//todo register accMods here
//		pre_accMods.add(new ageCar(scenario.getPopulation(), teleportTime_h, teleportDist_m, departureTime_h));
//		post_accMods.add(new economic_statusCar());

		// drt params
		this.betaCarTT_h = scoringConfigGroup.getModes().get(TransportMode.car).getMarginalUtilityOfTraveling() - scoringConfigGroup.getPerforming_utils_hr();
		this.betaCarDist_m = scoringConfigGroup.getModes().get(TransportMode.car).getMarginalUtilityOfDistance();

		// euros per meter
		this.betaCarDistMonetary_m = scoringConfigGroup.getModes().get(TransportMode.car).getMonetaryDistanceRate();

		this.marginalUtilityOfMoney = scoringConfigGroup.getMarginalUtilityOfMoney();


		this.globalAverageIncome = scenario.getPopulation().getPersons().values().stream()
			.filter(person -> PersonUtils.getIncome(person) != null) //consider only agents that have a specific income provided
			.mapToDouble(PersonUtils::getIncome)
			.filter(dd -> dd > 0)
			.average().getAsDouble();

	}


	@Override
	public void initialize(ActivityFacilities measuringPoints, ActivityFacilities opportunities) {
		LOG.warn("Initializing calculator for mode " + mode + "...");
		LOG.warn("Full network has " + scenario.getNetwork().getNodes().size() + " nodes.");
        subNetwork = NetworkUtils.createNetwork(networkConfigGroup);
        Set<String> modeSet = new HashSet<>();
        TransportModeNetworkFilter filter = new TransportModeNetworkFilter(scenario.getNetwork());
        if (mode.equals(Modes4Accessibility.freespeed.name())) {
        	modeSet.add(TransportMode.car);
		} else {
        	modeSet.add(mode);
		}
        filter.filter(subNetwork, modeSet);
        if (subNetwork.getNodes().size() == 0) {
        	throw new RuntimeException("Network has 0 nodes for mode " + mode + ". Something is wrong.");
        }
		LOG.warn("sub-network for mode " + modeSet.toString() + " now has " + subNetwork.getNodes().size() + " nodes.");

        this.aggregatedMeasurePoints = AccessibilityUtils.aggregateMeasurePointsWithSameNearestNode(measuringPoints, subNetwork);
		this.aggregatedOpportunities = AccessibilityUtils.aggregateOpportunitiesWithSameNearestNode(opportunities, subNetwork, scenario.getConfig());
	}


	@Override
	public void notifyNewOriginNode(Id<? extends BasicLocation> fromNodeId, Double departureTime) {
		this.fromNode = subNetwork.getNodes().get(fromNodeId);
		this.lcpt.calculate(subNetwork, fromNode, departureTime);
		//this.dijkstraTree.calcLeastCostPathTree(fromNode, departureTime);
		//multiNodePathCalculator.calcLeastCostPath(fromNode, aggregatedToNodes, departureTime, null, null);
	}


	@Override
	public double computeContributionOfOpportunity(ActivityFacility origin,
			Map<Id<? extends BasicLocation>, AggregationObject> aggregatedOpportunities, Double departureTime) {
		double expSum = 0.;

		Link nearestLink = NetworkUtils.getNearestLinkExactly(subNetwork, origin.getCoord());
		Distances distance = NetworkUtil.getDistances2NodeViaGivenLink(origin.getCoord(), nearestLink, fromNode);
		double walkTravelTimeMeasuringPoint2Road_h = distance.getDistancePoint2Intersection() / (this.walkSpeed_m_s * 3600);
		// Orthogonal walk to nearest link
		double walkUtilityMeasuringPoint2Road = (walkTravelTimeMeasuringPoint2Road_h * betaWalkTT);
		// NEW AV MODE
		//		double waitingTime_h = (Double) origin.getAttributes().getAttribute("waitingTime_s") / 3600.;
		//		double walkUtilityMeasuringPoint2Road = ((walkTravelTimeMeasuringPoint2Road_h + waitingTime_h) * betaWalkTT)
		//					+ (distance.getDistancePoint2Intersection() * betaWalkTD);
		// END NEW AV MODE

		// Travel on section of first link to first node
		double distanceFraction = distance.getDistanceIntersection2Node() / nearestLink.getLength();
		double congestedCarUtilityRoad2Node = -travelDisutility.getLinkTravelDisutility(nearestLink, departureTime, null, null) * distanceFraction;

		// Combine all utility components (using the identity: exp(a+b) = exp(a) * exp(b))
		double modeSpecificConstant = AccessibilityUtils.getModeSpecificConstantForAccessibilities(mode, scoringConfigGroup);

		for (final AggregationObject destination : aggregatedOpportunities.values()) {



			// Remaining travel on network

			double congestedCarUtility = -lcpt.getTree().get(((Node) destination.getNearestBasicLocation()).getId()).getCost();
			//double congestedCarUtility = - dijkstraTree.getLeastCostPath(destination.getNearestNode()).travelCost;
			//double congestedCarUtility = - multiNodePathCalculator.constructPath(fromNode, destination.getNearestNode(), departureTime).travelCost;

			// Pre-computed effect of all opportunities reachable from destination network node
			double sumExpVjkWalk = destination.getSum();

				expSum += Math.exp(this.scoringConfigGroup.getBrainExpBeta() * (walkUtilityMeasuringPoint2Road + modeSpecificConstant
					+ congestedCarUtilityRoad2Node + congestedCarUtility)) * sumExpVjkWalk;
		}
		return expSum;
	}


	// Needed if MultiNodePathCalculator is used as router -- experimental
//	public void setToNodes(ImaginaryNode aggregatedToNodes) {
//		log.warn("Setting toNodes.");
//		this.aggregatedToNodes = aggregatedToNodes;
//	}

	//todo implement personbasedacc calculation for car  + replace ActivityFacility origin into Person person and use their home coord instead

	public double computeContributionOfOpportunityPerson(Person person, Map<Id<? extends BasicLocation>, AggregationObject> aggregatedOpportunities, Double departureTime) {
		double expSum = 0.;


		// build facility around home...
		Facility homeFacility = FacilitiesUtils.wrapActivity((Activity) person.getSelectedPlan().getPlanElements().get(0));


		// find nearest link to home
		Link nearestLink = NetworkUtils.getNearestLinkExactly(subNetwork, homeFacility.getCoord());

		// Distances includes : (1) distanceCoord2Intersection with Link ; (2) distance along link to node (DRIVEN)
		Distances distance = NetworkUtil.getDistances2NodeViaGivenLink(homeFacility.getCoord(), nearestLink, fromNode);

		// first we deal with (1) distance from home -> link (Orthogonal walk to nearest link; (a) tt, (b) utility
		double walkTravelTimeMeasuringPoint2Road_h = distance.getDistancePoint2Intersection() / (this.walkSpeed_m_s * 3600); //walkutil from home>nearest road
		double walkUtilityMeasuringPoint2Road = (walkTravelTimeMeasuringPoint2Road_h * betaWalkTT);

		// now we deal with (2); Travel on section of first link to first node (DRIVEN)
		double distanceFraction = distance.getDistanceIntersection2Node() / nearestLink.getLength();
		double congestedCarUtilityRoad2Node = -travelDisutility.getLinkTravelDisutility(nearestLink, departureTime, null, null) * distanceFraction;

		// grab the ASC
		double modeSpecificConstant = AccessibilityUtils.getModeSpecificConstantForAccessibilities(mode, scoringConfigGroup);

		//todo applying pre accMods using interface (age)
//		for (pre_accModsInterface mods : pre_accMods){
//			double marginalUtilityOfMoney = mods.apply(person, teleportTime_h, teleportDist_m, departureTime_h); //todo adjust mods interface specifically for car (parameters)
//		}

		for (final AggregationObject destination : aggregatedOpportunities.values()) {


			ActivityFacilitiesFactory factory = scenario.getActivityFacilities().getFactory();
			ActivityFacility opportunity = factory.createActivityFacility(Id.create("dummy", ActivityFacility.class), destination.getNearestBasicLocation().getCoord());

			// Remaining travel on network
			List<? extends PlanElement> planElements = tripRouter.calcRoute(TransportMode.car, homeFacility, opportunity, departureTime, person, null);
			Leg mainLeg = extractLeg(planElements, TransportMode.car);

			// utility lost by time
			double timeCar = mainLeg.getTravelTime().seconds();
			double utilityTimeCar = timeCar / 3600 * betaCarTT_h;

			// utility lost by distance
			double distCar = mainLeg.getRoute().getDistance();
			double utilityDistCar = distCar * betaCarDist_m;

			double incomeFactor = this.globalAverageIncome / PersonUtils.getIncome(person);

			double utilityDistCarMonetary = distCar * betaCarDistMonetary_m * marginalUtilityOfMoney * incomeFactor;

			double congestedCarUtility = utilityTimeCar + utilityDistCar + utilityDistCarMonetary;




//			double congestedCarUtility = -lcpt.getTree().get(((Node) destination.getNearestBasicLocation()).getId()).getCost();

//			-lcpt.getTree().get(((Node) destination.getNearestBasicLocation()).getId()).
			//double congestedCarUtility = - dijkstraTree.getLeastCostPath(destination.getNearestNode()).travelCost;
			//double congestedCarUtility = - multiNodePathCalculator.constructPath(fromNode, destination.getNearestNode(), departureTime).travelCost;

			// Pre-computed effect of all opportunities reachable from destination network node
			double sumExpVjkWalk = destination.getSum();

			double contribution = Math.exp(this.scoringConfigGroup.getBrainExpBeta() * (walkUtilityMeasuringPoint2Road + modeSpecificConstant
				+ congestedCarUtilityRoad2Node + congestedCarUtility)) * sumExpVjkWalk;

//			for (post_accModsInterface mods : post_accMods){
//				contribution = mods.apply(person, walkTravelTimeMeasuringPoint2Road_h, distanceFraction, departureTime, contribution);
//			}
			// total accessibility including economic_status penalty
			expSum += contribution;
		}
		return expSum;
	}

	@Override
	public NetworkModeAccessibilityExpContributionCalculator duplicate() {
		LOG.info("Creating another NetworkModeAccessibilityExpContributionCalculator object.");
		NetworkModeAccessibilityExpContributionCalculator networkModeAccessibilityExpContributionCalculator =
				new NetworkModeAccessibilityExpContributionCalculator(this.mode, this.travelTime, this.travelDisutilityFactory, this.tripRouter,this.scenario);
		networkModeAccessibilityExpContributionCalculator.subNetwork = this.subNetwork;
		networkModeAccessibilityExpContributionCalculator.aggregatedMeasurePoints = this.aggregatedMeasurePoints;
		networkModeAccessibilityExpContributionCalculator.aggregatedOpportunities = this.aggregatedOpportunities;
		return networkModeAccessibilityExpContributionCalculator;
	}


	@Override
    public Map<Id<? extends BasicLocation>, ArrayList<ActivityFacility>> getAggregatedMeasurePoints() {
        return aggregatedMeasurePoints;
    }


	@Override
	public Map<Id<? extends BasicLocation>, AggregationObject> getAgregatedOpportunities() {
		return aggregatedOpportunities;
	}
}
