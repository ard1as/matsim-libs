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
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.FacilitiesConfigGroup;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacilitiesFactory;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.ActivityOption;

import java.io.FileWriter;
import java.io.IOException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RunPersonBasedAccBerlin {

	private static final String OUTPUT_ROOT = "../public-svn/matsim/scenarios/countries/de/berlin/projects/fabilut/output-1pct/";
	private static final String OUTPUT_BASE = OUTPUT_ROOT + "base/";
	private static final String OUTPUT_POLICY = OUTPUT_ROOT + "policy/";
	private static String OUTPUT_DIR;

	private static final String CRS = "EPSG:25832";

	private static Scenario scenario;

	private static final Logger LOG = LogManager.getLogger(RunPersonBasedAccBerlin.class);

	private enum RunScenario {base, policy}

	public static void main(String[] args) {
//		== Run options ==
		boolean personBased = false; //todo select accessibility measure (true for person-based/false for location-based)
		RunScenario runScenario = RunScenario.base; //todo select scenario (base/policy)
		Modes4Accessibility targetMode = Modes4Accessibility.car; //todo select transport mode (car/pt/teleportedWalk)
		AccessibilityConfigGroup.AccessibilityMeasureType measureType = AccessibilityConfigGroup.AccessibilityMeasureType.rawSum; //todo select measure type (logSum/rawSum, rawSum for multimodal R analysis)

		OUTPUT_DIR = OUTPUT_ROOT + runScenario + "/";
		loadFiles(personBased, targetMode, measureType);


//		== Calculate accessibility ==
		String eventsFile = OUTPUT_DIR + "berlin-v6.3.output_events.xml.gz";
		long start = System.currentTimeMillis();
		String accessibilityType = personBased ? "person-based" : "location-based";
		LOG.info(
			"Calculating {} {} accessibility for {} scenario using {}...",
			accessibilityType,
			targetMode,
			runScenario,
			measureType
		);

		AccessibilityFromEvents.Builder builder = new AccessibilityFromEvents.Builder(scenario, eventsFile, List.of("spa"));

		PersonBasedResultsComparator dataListener = null;
		if (personBased) {
			dataListener = new PersonBasedResultsComparator();
			builder.addDataListener(dataListener);
		}
		builder.build().run() ;

		long ms = System.currentTimeMillis() - start;
		long seconds = ms / 1000;
		long minutes = seconds / 60;
		LOG.info("Accessibility computation finished in {} minutes (≈ {} seconds)", minutes, seconds);

		if (personBased){
			Map<Tuple<Person, Double>, Map<String, Double>> accessibilitiesMap = dataListener.getAccessibilitiesMap();
			writeCSV(accessibilitiesMap, OUTPUT_DIR + "pbAcc_" + targetMode + "_" + runScenario + "_" + measureType + ".csv");
		}
	}

	private static void loadFiles(boolean personBased, Modes4Accessibility targetMode, AccessibilityConfigGroup.AccessibilityMeasureType measureType) {

//		== Input Files ==
		String networkFile 			= OUTPUT_DIR + "berlin-v6.3.output_network.xml.gz";
		//String facilitiesFile 	= OUTPUT_DIR + "berlin-v6.3.output_facilities.xml.gz";//manually set facilities
		String plansFile			= OUTPUT_BASE + "berlin-v6.3.output_plans.xml.gz";
		String transitScheduleFile 	= OUTPUT_DIR + "berlin-v6.3.output_transitSchedule.xml.gz";

//		== Create config ==
		final Config config = ConfigUtils.createConfig();

//		== Adjust config ==
		config.controller().setLastIteration(0);
		config.controller().setOutputDirectory(OUTPUT_DIR);
		config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.overwriteExistingFiles);
		config.global().setCoordinateSystem(CRS);
		config.routing().setRoutingRandomness(0.);


		// add monetary distance rate for car
		config.scoring().getModes().get(TransportMode.car).setMonetaryDistanceRate(-0.0002);

		config.network().setInputFile(networkFile);
		//config.facilities().setInputFile(facilitiesFile);
		config.facilities().setFacilitiesSource(FacilitiesConfigGroup.FacilitiesSource.none);
		config.plans().setInputFile(plansFile);
		config.transit().setTransitScheduleFile(transitScheduleFile);


//		== Accessibility config ==
		AccessibilityConfigGroup acg = ConfigUtils.addOrGetModule(config, AccessibilityConfigGroup.class);
		acg.setPersonBased(personBased);
		acg.setAccessibilityMeasureType(measureType);
		acg.setTimeOfDay(8*60*60.);
		acg.setTileSize_m(1000);
		if (personBased) {
			acg.setAreaOfAccessibilityComputation(
				AccessibilityConfigGroup.AreaOfAccesssibilityComputation.fromPopulation
			);
		} else {
			acg.setAreaOfAccessibilityComputation(
				AccessibilityConfigGroup.AreaOfAccesssibilityComputation.fromBoundingBox
			);
		}

//		== Mode selection ==
		List<Modes4Accessibility> accModes = List.of(targetMode);
		for(Modes4Accessibility mode : Modes4Accessibility.values()) {
			acg.setComputingAccessibilityForMode(mode, accModes.contains(mode));
		}


//		== Load scenario ==
		scenario = ScenarioUtils.loadScenario(config);

//		== Adding facilities ==
		ActivityFacilities activityFacilities = scenario.getActivityFacilities();
		ActivityFacilitiesFactory af = activityFacilities.getFactory();
		ActivityOption aoSpa = af.createActivityOption("spa");

		addFacility(activityFacilities, af, aoSpa, "vabali", 795634.64, 5828763.74);//001_Mitte
		addFacility(activityFacilities, af, aoSpa, "liquidrom", 797312.78, 5825825.94);//002_Friedrichshain-Kreuzberg
		addFacility(activityFacilities, af, aoSpa, "meridian", 784623.35, 5828694.12);//005_Spandau
		addFacility(activityFacilities, af, aoSpa, "aspria", 791428.73, 5825366.54);//004_Charlottenburg-Wilmersdorf
		addFacility(activityFacilities, af, aoSpa, "hamam", 800182.05, 5825953.76);//002_Friedrichshain-Kreuzberg
		addFacility(activityFacilities, af, aoSpa, "saunabad", 800320.0, 5829910.0);//003_Pankow
		addFacility(activityFacilities, af, aoSpa, "kristall", 786190.0, 5791400.0);//NA (Ludwigsfelde)


//		== Assigning home coords for each person ==
		int[] kept = {0};
		int[] skipped = {0};
		int[] limit = {0};
		scenario.getPopulation().getPersons().values().removeIf(person -> {
//			if (limit[0] >= 1000){	//todo set limit how many person(s) get parsed
//				skipped[0]++;
//				return true;
//			}

			Plan selectedPlan = person.getSelectedPlan();
			if (selectedPlan == null) {
				LOG.warn("Skipping {} (no plan)", person.getId());
				skipped[0]++;
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
				skipped[0]++;
				return true;
			}

			// Store as attributes so AccessibilityModule can find them
			person.getAttributes().putAttribute("homeX", home.getCoord().getX());
			person.getAttributes().putAttribute("homeY", home.getCoord().getY());

			kept[0]++;
			limit[0]++;
			return false;
		});
		LOG.info("Sanity check: {} persons kept with valid home coords, {} skipped", kept[0], skipped[0]);
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
		LOG.info("Bounding box set to: minX={}, maxX={}, minY={}, maxY={}", minX, maxX, minY, maxY);

//		== Run ==
		LOG.info("Running person-based accessibility computation for Berlin...");

	}

	private static void addFacility(
		ActivityFacilities facilities,
		ActivityFacilitiesFactory factory,
		ActivityOption activityOption,
		String id,
		double x,
		double y){
		ActivityFacility facility = factory.createActivityFacility(
			Id.create(id, ActivityFacility.class),
			new Coord(x, y)
		);
		facility.addActivityOption(activityOption);
		facilities.addActivityFacility(facility);
	}

	private static class PersonBasedResultsComparator implements PersonDataExchangeInterface {
		private final Map<Tuple<Person, Double>, Map<String,Double>> accessibilitiesMap = new HashMap<>() ;

		@Override
		public synchronized void setPersonAccessibilities(Person person, Double timeOfDay, String mode, double accessibility) {
			Tuple<Person, Double> key = new Tuple<>(person, timeOfDay);
//			if (!accessibilitiesMap.containsKey(key)) {
//				Map<String,Double> accessibilitiesByMode = new HashMap<>();
//				accessibilitiesMap.put(key, accessibilitiesByMode);
//			}
			/*FIX: using computeIfAbsent to ensure inner map (mode → accessibility values)
			always exists for each (person, time) tuple. without this, accessibilitiesMap.get(key)
			could return null, causing NullPointerException when calling .put(mode, accessibility).*/
			accessibilitiesMap.computeIfAbsent(key, k -> new HashMap<>());
			accessibilitiesMap.get(key).put(mode, accessibility);
		}

		public Map<Tuple<Person, Double>, Map<String, Double>> getAccessibilitiesMap() {
			return accessibilitiesMap;
		}

		@Override
		public void finish() {

		}
	}

	private static void writeCSV(Map<Tuple<Person, Double>, Map<String, Double>> accessibilitiesMap, String csvFile) {
		try (FileWriter writer = new FileWriter(csvFile)) {
			// Write header
			writer.write("personId,age,sex,income,carAvail,restricted_mobility,homeX,homeY,accessibility\n");//removed time & mode

			// Write data
			for (var entry : accessibilitiesMap.entrySet()) {
				Person person = entry.getKey().getFirst();
				Map<String, Double> modeAccs = entry.getValue();

				// Get attributes
				Object age = person.getAttributes().getAttribute("age");
				Object sex = person.getAttributes().getAttribute("sex");
				Object income = person.getAttributes().getAttribute("income");
				Object carAvail = person.getAttributes().getAttribute("carAvail");
				Object restricted = person.getAttributes().getAttribute("restricted_mobility");
				Object homeX = person.getAttributes().getAttribute("homeX");
				Object homeY = person.getAttributes().getAttribute("homeY");

				for (Map.Entry<String, Double> modeAcc : modeAccs.entrySet()) {
					writer.write(person.getId() + ","
						+ (age != null ? age : "") + ","
						+ (sex != null ? sex : "") + ","
						+ (income != null ? income : "") + ","
						+ (carAvail != null ? carAvail : "") + ","
						+ (restricted != null ? restricted : "") + ","
						+ (homeX != null ? homeX : "") + ","
						+ (homeY != null ? homeY : "") + ","
						+ modeAcc.getValue() + "\n");
				}
			}
		} catch (IOException e) {
			throw new RuntimeException("Error writing CSV", e);
		}
		LOG.info("Custom CSV written to {}", csvFile);
	}

}
