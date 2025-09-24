package org.matsim.contrib.accessibility.run;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.contrib.accessibility.*;
import org.matsim.contrib.accessibility.utils.NetworkUtil;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigReader;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RunPersonBasedAccBerlin {

	static String OUTPUT_DIR = "../public-svn/matsim/scenarios/countries/de/berlin/projects/fabilut/output-1pct/policy";
	private static final String crs = "EPSG:25832";

	private static Scenario scenario;

	private static final Logger LOG = LogManager.getLogger(RunPersonBasedAccBerlin.class);

	public static void main(String[] args) {
		LoadFiles();
//		CreateActivities();//create plans for each person to all available activities
		CalculateAccessibility(scenario);
	}

	private static void LoadFiles() {

//		== Input Files ==
		String configFile = "D:/git/public-svn/matsim/scenarios/countries/de/berlin/projects/fabilut/output-1pct/policy/berlin-v6.3.output_config.xml";
		String networkFile = "D:/git/public-svn/matsim/scenarios/countries/de/berlin/projects/fabilut/output-1pct/policy/berlin-v6.3.output_network.xml.gz";
		String plansFile = "D:/git/public-svn/matsim/scenarios/countries/de/berlin/projects/fabilut/output-1pct/policy/berlin-v6.3.output_plans.xml.gz";
		String facilitiesFile = "D:/git/public-svn/matsim/scenarios/countries/de/berlin/projects/fabilut/output-1pct/policy/berlin-v6.3.output_facilities.xml.gz";
		String transitScheduleFile = "D:/git/public-svn/matsim/scenarios/countries/de/berlin/projects/fabilut/output-1pct/policy/berlin-v6.3.output_transitSchedule.xml.gz";
//		String transitVehiclesFile = "D:/git/public-svn/matsim/scenarios/countries/de/berlin/projects/fabilut/output-1pct/policy/berlin-v6.3.output_transitVehicles.xml.gz";

//		== Load config ==
		Config config = ConfigUtils.loadConfig(configFile, new AccessibilityConfigGroup());

//		== Adjust config ==
		config.controller().setLastIteration(0);
		config.controller().setOutputDirectory(OUTPUT_DIR);
		config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.overwriteExistingFiles);
		config.global().setCoordinateSystem(crs);
		config.routing().setRoutingRandomness(0.);

		config.network().setInputFile(networkFile);
		config.plans().setInputFile(plansFile);
		config.facilities().setInputFile(facilitiesFile);
		config.transit().setTransitScheduleFile(transitScheduleFile);
//		config.transit().setTransitScheduleFile(transitVehiclesFile);


//		== Accessibility config ==
		AccessibilityConfigGroup acg = ConfigUtils.addOrGetModule(config, AccessibilityConfigGroup.class);
		acg.setPersonBased(true);
		acg.setTileSize_m(100);
		acg.setAreaOfAccessibilityComputation(AccessibilityConfigGroup.AreaOfAccesssibilityComputation.fromPopulation);

//		== Mode selection ==
		List<Modes4Accessibility> accModes = List.of(Modes4Accessibility.teleportedWalk);
		for(Modes4Accessibility mode : Modes4Accessibility.values()) {
			acg.setComputingAccessibilityForMode(mode, accModes.contains(mode));
		}

//		== Load scenario ==
		scenario = ScenarioUtils.loadScenario(config);

//		== Controller ==
		Controler controler = new Controler(scenario);

//		== Add accessibility module	==
		AccessibilityModule accessibilityModule = new AccessibilityModule();
		controler.addOverridingModule(accessibilityModule);

//		== Run ==
		LOG.info("Running person-based accessibility computation for Berlin...");
		controler.run();

	}

//todo	need to fix object scenario either make usable in different method or combine with LoadScenario();
//	private static void CreateActivities(String personId) {
//		for (Person person : scenario.getPopulation().getPersons().values()){
//			person.getPlans().clear();
//			Plan plan = scenario.getPopulation().getFactory().createPlan();
//			Double homeX = (Double) person.getAttributes().getAttribute("homeX");
//			Double homeY = (Double) person.getAttributes().getAttribute("homeY");
//			if (homeX != null && homeY != null) {
//				plan.addActivity(scenario.getPopulation().getFactory()
//					.createActivityFromCoord("home", new Coord(homeX, homeY))); //todo check how the activity is being created to all available facilities
//			} else {
//				LOG.warn("No home coordinates for person " + person.getId());
//			}
//			person.addPlan(plan);
//			person.setSelectedPlan(plan);
//		}
//
////		Person person = scenario.getPopulation().getFactory().createPerson(Id.createPersonId(personId));
////		Plan plan = scenario.getPopulation().getFactory().createPlan();
////		plan.addActivity(
////			scenario.getPopulation().getFactory().createActivityFromCoord("home", new Coord(homeX, homeY)));
////		person.addPlan(plan);
////		person.setSelectedPlan(plan);
////		scenario.getPopulation().addPerson(person);
//	}


	private static Map<String, Double> CalculateAccessibility(Scenario scenario) {
		String eventsFile = "D:/git/public-svn/matsim/scenarios/countries/de/berlin/projects/fabilut/output-1pct/policy/berlin-v6.3.output_events.xml.gz";

		AccessibilityFromEvents.Builder builder = new AccessibilityFromEvents.Builder( scenario , eventsFile );
		PersonBasedResultsComparator dataListener = new PersonBasedResultsComparator();
		builder.addDataListener(dataListener);
		builder.build().run() ;

		Map<Tuple<Person, Double>, Map<String, Double>> accessibilitiesMap = dataListener.getAccessibilitiesMap();



		Map<String, Double> personAccMap = accessibilitiesMap.entrySet()
			.stream()
			.collect(Collectors.toMap(
				entry -> entry.getKey().getFirst().getId().toString(),
				entry -> entry.getValue().get("teleportedWalk")
			));
		System.out.println(personAccMap);
		return personAccMap;
	}

	private static class PersonBasedResultsComparator implements PersonDataExchangeInterface {
		private final Map<Tuple<Person, Double>, Map<String,Double>> accessibilitiesMap = new HashMap<>() ;

		@Override
		public void setPersonAccessibilities(Person person, Double timeOfDay, String mode, double accessibility) {
			Tuple<Person, Double> key = new Tuple<>(person, timeOfDay);
			if (!accessibilitiesMap.containsKey(key)) {
				Map<String,Double> accessibilitiesByMode = new HashMap<>();
				accessibilitiesMap.put(key, accessibilitiesByMode);
			}
			accessibilitiesMap.get(key).put(mode, accessibility);
		}

		public Map<Tuple<Person, Double>, Map<String, Double>> getAccessibilitiesMap() {
			return accessibilitiesMap;
		}

		@Override
		public void finish() {

		}
	}

}
