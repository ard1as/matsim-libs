package org.matsim.contrib.accessibility.run;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Activity;
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
//		== Calculate accessibility ==
		String eventsFile = "D:/git/public-svn/matsim/scenarios/countries/de/berlin/projects/fabilut/output-1pct/policy/berlin-v6.3.output_events.xml.gz";
		LOG.info("Calculating person-based accessibility for Berlin...");

		AccessibilityFromEvents.Builder builder = new AccessibilityFromEvents.Builder(scenario, eventsFile);
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
	}

	private static void LoadFiles() {

//		== Input Files ==
		String configFile = "D:/git/public-svn/matsim/scenarios/countries/de/berlin/projects/fabilut/output-1pct/policy/berlin-v6.3.output_config.xml";//for now redundant
		String networkFile = "D:/git/public-svn/matsim/scenarios/countries/de/berlin/projects/fabilut/output-1pct/policy/berlin-v6.3.output_network.xml.gz";
		String facilitiesFile = "D:/git/public-svn/matsim/scenarios/countries/de/berlin/projects/fabilut/output-1pct/policy/berlin-v6.3.output_facilities.xml.gz";
		String plansFile = "D:/git/public-svn/matsim/scenarios/countries/de/berlin/projects/fabilut/output-1pct/policy/berlin-v6.3.output_plans.xml.gz";
		String transitScheduleFile = "D:/git/public-svn/matsim/scenarios/countries/de/berlin/projects/fabilut/output-1pct/policy/berlin-v6.3.output_transitSchedule.xml.gz";

//		== Create config ==
		final Config config = ConfigUtils.createConfig();

//		== Adjust config ==
		config.controller().setLastIteration(0);
		config.controller().setOutputDirectory(OUTPUT_DIR);
		config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.overwriteExistingFiles);
		config.global().setCoordinateSystem(crs);
		config.routing().setRoutingRandomness(0.);

		config.network().setInputFile(networkFile);
		config.facilities().setInputFile(facilitiesFile);
		config.plans().setInputFile(plansFile);
		config.transit().setTransitScheduleFile(transitScheduleFile);


//		== Accessibility config ==
		AccessibilityConfigGroup acg = ConfigUtils.addOrGetModule(config, AccessibilityConfigGroup.class);
		acg.setPersonBased(true);
		acg.setTileSize_m(100);
		acg.setAreaOfAccessibilityComputation(AccessibilityConfigGroup.AreaOfAccesssibilityComputation.fromPopulation);//base accessibility from population
		acg.setTimeOfDay(8*60*60.);

//		== Mode selection ==
		List<Modes4Accessibility> accModes = List.of(Modes4Accessibility.teleportedWalk);
		for(Modes4Accessibility mode : Modes4Accessibility.values()) {
			acg.setComputingAccessibilityForMode(mode, accModes.contains(mode));
		}

//		== Load scenario ==
		scenario = ScenarioUtils.loadScenario(config);

//		== Assigning home coords for each person ==
		int[] counters = {0, 0};
		int[] limitCounter = {0};
		scenario.getPopulation().getPersons().values().removeIf(person -> {
			if (limitCounter[0] >= 100){	//limit how many person(s) get parsed
				counters[1]++;
				return true;
			}

			Plan selectedPlan = person.getSelectedPlan();
			if (selectedPlan == null) {
				LOG.warn("Skipping {} (no plan)", person.getId());
				counters[1]++;
				return true;
			}

			// Find first home activity in the plan
			Activity home = selectedPlan.getPlanElements().stream()
				.filter(pe -> pe instanceof Activity)
				.map(pe -> (Activity) pe)
				.filter(activity -> activity.getType().startsWith("home"))
				.findFirst()
				.orElse(null);

			if (home == null || home.getCoord() == null) {
				LOG.warn("Skipping {} (no home activity/coord)", person.getId());
				counters[1]++;
				return true;
			}

			// Store as attributes so AccessibilityModule can find them
			person.getAttributes().putAttribute("homeX", home.getCoord().getX());
			person.getAttributes().putAttribute("homeY", home.getCoord().getY());

			counters[0]++;
			limitCounter[0]++;
			return false;
		});
		LOG.info("Sanity check: {} persons kept with valid home coords, {} skipped", counters[0], counters[1]);
		LOG.info("Final population size after filtering: {}", scenario.getPopulation().getPersons().size());


//		== Bounding box from persons home coords ==
		double minX = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;

		for (Person person : scenario.getPopulation().getPersons().values()) {
			Double x = (Double) person.getAttributes().getAttribute("homeX");
			Double y = (Double) person.getAttributes().getAttribute("homeY");
			if (x != null && y != null) {
				minX = Math.min(minX, x);
				maxX = Math.max(maxX, x);
				minY = Math.min(minY, y);
				maxY = Math.max(maxY, y);
			}
		}
		acg.setBoundingBoxBottom(minY).setBoundingBoxTop(maxY)
			.setBoundingBoxLeft(minX).setBoundingBoxRight(maxX);
		LOG.info("Bounding box set to: minX={}, maxX={}, minY={}, maxY={}", minX, maxX, minY, maxY); // NEW

//		== Run ==
		LOG.info("Running person-based accessibility computation for Berlin...");

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
