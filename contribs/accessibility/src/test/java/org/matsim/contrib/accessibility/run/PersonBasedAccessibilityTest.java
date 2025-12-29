package org.matsim.contrib.accessibility.run;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.contrib.accessibility.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.facilities.ActivityFacility;
import org.matsim.testcases.MatsimTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.matsim.contrib.accessibility.run.TinyAccessibilityTest.createTestScenario;


// STEP 1: make accessibility calculation depend on home location, but not on person attributes
public class PersonBasedAccessibilityTest {

	private static final Logger LOG = LogManager.getLogger(PersonBasedAccessibilityTest.class);
	private static final double EPS = 1e-6;

	@RegisterExtension
	private MatsimTestUtils utils = new MatsimTestUtils();

	@Test
	void testPersonBasedAccessibilityForCar() {
		final Config config = ConfigUtils.createConfig();

		final AccessibilityConfigGroup acg = ConfigUtils.addOrGetModule(config, AccessibilityConfigGroup.class);

		acg.setPersonBased(true);
		acg.setTileSize_m(100);
		List<Modes4Accessibility> accModes = List.of(Modes4Accessibility.car);

		for(Modes4Accessibility mode : Modes4Accessibility.values()) {
			acg.setComputingAccessibilityForMode(mode, accModes.contains(mode));
		}


		config.controller().setLastIteration(0);
		config.controller().setOutputDirectory(utils.getOutputDirectory());
		config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		config.routing().setRoutingRandomness(0.);
		config.scoring().getModes().get(TransportMode.car).setMonetaryDistanceRate(-0.0002);


		double min = 0.; // Values for bounding box usually come from a config file
		double max = 200.;

		acg.setAreaOfAccessibilityComputation(AccessibilityConfigGroup.AreaOfAccesssibilityComputation.fromPopulation);
		acg.setBoundingBoxBottom(min).setBoundingBoxTop(max ).setBoundingBoxLeft(min).setBoundingBoxRight(max );
		acg.setUseParallelization(false);
		acg.setTimeOfDay(8*60*60.);

		// ---

		final Scenario scenario = createTestScenario(config);

		// add test person
		addPerson(scenario, "young", 10, 1000, "low", 100, 100, false, "f");
		addPerson(scenario, "middle", 30, 1000, "high", 150, 150, false, "m");
		addPerson(scenario, "old", 90, 2000, "medium", 150, 150, true, "f");
		addPerson(scenario, "old+low", 90, 1000, "low", 150, 150, true, "m");

		//person for income test
		addPerson(scenario, "low_income", 30, 500, "low", 0,0, false, "m");
		addPerson(scenario, "medium_income",30, 2000, "medium", 0,0, false, "f");
		addPerson(scenario, "high_income", 30, 5000, "high", 0, 0, false, "f");
//		addPerson(scenario, "medium_income_2",30, 2000, "medium", 100, 100, false, "m");

		// ---

		final String eventsFile = utils.getClassInputDirectory() + "output_events.xml.gz";

		AccessibilityFromEvents.Builder builder = new AccessibilityFromEvents.Builder( scenario , eventsFile );
		PersonBasedResultsComparator dataListener = new PersonBasedResultsComparator();
		builder.addDataListener(dataListener);
		builder.build().run() ;

		Map<Tuple<Person, Double>, Map<String, Double>> accessibilitiesMap = dataListener.getAccessibilitiesMap();

		Map<String, Double> personAccMap = accessibilitiesMap.entrySet()
			.stream()
			.collect(Collectors.toMap(
				entry -> entry.getKey().getFirst().getId().toString(),
				entry -> entry.getValue().get("car")
			));

		/*
		Assertions.assertEquals(personAccMap.get("testPerson_young"), personAccMap.get("testPerson_middle"));
		Assertions.assertEquals(personAccMap.get("testPerson_old"), personAccMap.get("testPerson_middle") - 10.);
		Assertions.assertEquals(personAccMap.get("testPerson_old"), personAccMap.get("testPerson_young") - 10.);
		 */

		System.out.println((personAccMap.get("testPerson_low_income")));
		System.out.println((personAccMap.get("testPerson_medium_income")));
		System.out.println((personAccMap.get("testPerson_high_income")));

		//todo tests based on relative differences instead of absolute values
		//low < medium income
		Assertions.assertTrue(personAccMap.get("testPerson_low_income") < personAccMap.get("testPerson_medium_income"), "low < medium income");
		//low < high income
		Assertions.assertTrue(personAccMap.get("testPerson_low_income") < personAccMap.get("testPerson_high_income"), "low < high income");
		//medium < high income
		Assertions.assertTrue(personAccMap.get("testPerson_medium_income") < personAccMap.get("testPerson_high_income"), "medium < high income");

		System.out.println(personAccMap);


//
//		// print results
//		accessibilitiesMap.forEach( (personTime, mode2acc) -> {
//			Person person = personTime.getFirst();
//			Double time = personTime.getSecond();
//			LOG.info("Person " + person.getId() + " at time " + time);
//			mode2acc.forEach( (mode, acc) -> LOG.info("  mode: " + mode + " acc: " + acc ) );
//		});


	}

	@Test
	void testAgeTeleportedWalk(){
		final Config config = ConfigUtils.createConfig();

		final AccessibilityConfigGroup acg = ConfigUtils.addOrGetModule(config, AccessibilityConfigGroup.class);

		acg.setPersonBased(true);
		acg.setTileSize_m(100);
		List<Modes4Accessibility> accModes = List.of(Modes4Accessibility.teleportedWalk);

		for(Modes4Accessibility mode : Modes4Accessibility.values()) {
			acg.setComputingAccessibilityForMode(mode, accModes.contains(mode));
		}


		config.controller().setLastIteration(0);
		config.controller().setOutputDirectory(utils.getOutputDirectory());
		config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		config.routing().setRoutingRandomness(0.);
		config.scoring().getModes().get(TransportMode.car).setMonetaryDistanceRate(-0.0002);


		double min = 0.; // Values for bounding box usually come from a config file
		double max = 200.;

		acg.setAreaOfAccessibilityComputation(AccessibilityConfigGroup.AreaOfAccesssibilityComputation.fromPopulation);
		acg.setBoundingBoxBottom(min).setBoundingBoxTop(max ).setBoundingBoxLeft(min).setBoundingBoxRight(max );
		acg.setUseParallelization(false);
		acg.setTimeOfDay(8*60*60.);

		// ---

		final Scenario scenario = createTestScenario(config);

		// add test person
		addPerson(scenario, "young", 10, 1000, "low", 0, 0, false, "f");
		addPerson(scenario, "middle", 30, 1000, "high", 0, 0, false, "m");
		addPerson(scenario, "old", 90, 2000, "medium", 0, 0, true, "f");
		addPerson(scenario, "old+low", 90, 1000, "low", 150, 150, true, "m");

		//person for income test
		addPerson(scenario, "low_income", 30, 500, "low", 0,0, false, "m");
		addPerson(scenario, "medium_income",30, 2000, "medium", 0,0, false, "f");
		addPerson(scenario, "high_income", 30, 5000, "high", 0, 0, false, "f");
//		addPerson(scenario, "medium_income_2",30, 2000, "medium", 100, 100, false, "m");

		// ---

		final String eventsFile = utils.getClassInputDirectory() + "output_events.xml.gz";

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


		System.out.println((personAccMap.get("testPerson_young")));
		System.out.println((personAccMap.get("testPerson_middle")));
		System.out.println((personAccMap.get("testPerson_old")));

		//todo tests based on relative differences instead of absolute values
		//young > old age
		Assertions.assertTrue(personAccMap.get("testPerson_young") > personAccMap.get("testPerson_old"), "young > old age");
		//middle > old age
		Assertions.assertTrue(personAccMap.get("testPerson_middle") > personAccMap.get("testPerson_old"), "middle > old age");

		System.out.println(personAccMap);
	}

	//todo implement tests for other modes
	@Test
	void testSameLocationSameAccessibilityWhenPersonBasedOnAndEqualIncome(){
		// --- CONFIG ---
		Config config = ConfigUtils.createConfig();
		AccessibilityConfigGroup acg = ConfigUtils.addOrGetModule(config, AccessibilityConfigGroup.class);

		acg.setPersonBased(true);
		acg.setTileSize_m(100);
		acg.setAreaOfAccessibilityComputation(
			AccessibilityConfigGroup.AreaOfAccesssibilityComputation.fromPopulation
		);
		acg.setBoundingBoxBottom(0.).setBoundingBoxTop(200.)
			.setBoundingBoxLeft(0.).setBoundingBoxRight(200.);
		acg.setUseParallelization(false);
		acg.setTimeOfDay(8 * 60 * 60.);

		List<Modes4Accessibility> accModes = List.of(Modes4Accessibility.car);
		for (Modes4Accessibility mode : Modes4Accessibility.values()) {
			acg.setComputingAccessibilityForMode(mode, accModes.contains(mode));
		}

		config.controller().setLastIteration(0);
		config.controller().setOutputDirectory(utils.getOutputDirectory() + "/pbOnEqualIncome");
		config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		config.routing().setRoutingRandomness(0.);
		config.scoring().getModes().get(TransportMode.car).setMonetaryDistanceRate(-0.0002);

		// --- SCENARIO ---
		Scenario scenario = createTestScenario(config);

		double x = 100.;
		double y = 100.;
		double equalIncome = 2000.;

		// Two persons at the SAME location with the SAME income,
		// but different other attributes (age, economic status, restricted, sex)
		addPerson(scenario, "sameLocA", 25, equalIncome, "medium", x, y, false, "f");
		addPerson(scenario, "sameLocB", 45, equalIncome, "medium", x, y, true, "m");

		addPerson(scenario, "low_income", 30, equalIncome, "low", 0,0, false, "m");
		addPerson(scenario, "medium_income",30, equalIncome, "medium", 0,0, false, "f");
		addPerson(scenario, "high_income", 30, equalIncome, "high", 0, 0, false, "f");

		final String eventsFile = utils.getClassInputDirectory() + "output_events.xml.gz";

		// --- PERSON-BASED LISTENER ---
		PersonBasedResultsComparator listener = new PersonBasedResultsComparator();
		AccessibilityFromEvents.Builder builder =
			new AccessibilityFromEvents.Builder(scenario, eventsFile);
		builder.addDataListener(listener);
		builder.build().run();

		Map<Tuple<Person, Double>, Map<String, Double>> accessibilitiesMap =
			listener.getAccessibilitiesMap();

		// Map: personId -> car accessibility
		Map<String, Double> personAccMap = accessibilitiesMap.entrySet()
			.stream()
			.collect(Collectors.toMap(
				e -> e.getKey().getFirst().getId().toString(),
				e -> e.getValue().get("car"),
				(a, b) -> a // in case of duplicates for same person/time, keep first
			));

		double accA = personAccMap.get("testPerson_sameLocA");
		double accB = personAccMap.get("testPerson_sameLocB");

		// --- OUTPUT VALUES ---
		System.out.println("=== Person-Based Accessibility (person-based ON, equal income) ===");
		System.out.println("Person A (sameLocA) accessibility: " + accA);
		System.out.println("Person B (sameLocB) accessibility: " + accB);
		System.out.println("Difference: " + Math.abs(accA - accB));
		System.out.println("================================================================");

		// --- ASSERTION ---
		Assertions.assertEquals(
			accA,
			accB,
			EPS,
			"With person-based ON and equal income, people at the same location should have the same accessibility"
		);
	}

	@Test
	void testSameLocationSameAccessibilityWhenPersonBasedOff() {
		Config config = ConfigUtils.createConfig();
		AccessibilityConfigGroup acg = ConfigUtils.addOrGetModule(config, AccessibilityConfigGroup.class);

		// person-based OFF
		acg.setPersonBased(false);
		acg.setTileSize_m(100);
		acg.setAreaOfAccessibilityComputation(
			AccessibilityConfigGroup.AreaOfAccesssibilityComputation.fromBoundingBox
		);
		acg.setBoundingBoxBottom(0.).setBoundingBoxTop(200.)
			.setBoundingBoxLeft(0.).setBoundingBoxRight(200.);
		acg.setUseParallelization(false);
		acg.setTimeOfDay(8 * 60 * 60.);

		List<Modes4Accessibility> accModes = List.of(Modes4Accessibility.car);
		for (Modes4Accessibility mode : Modes4Accessibility.values()) {
			acg.setComputingAccessibilityForMode(mode, accModes.contains(mode));
		}

		config.controller().setLastIteration(0);
		config.controller().setOutputDirectory(utils.getOutputDirectory() + "/pbOffTest");
		config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		config.routing().setRoutingRandomness(0.);
		config.scoring().getModes().get(TransportMode.car).setMonetaryDistanceRate(-0.0002);

		Scenario scenario = createTestScenario(config);

		double x = 100.;
		double y = 100.;
		addPerson(scenario, "P1", 30, 1000, "low", x, y, false, "f");
		addPerson(scenario, "P2", 45, 2000, "medium", x, y, true, "m");

		final String eventsFile = utils.getClassInputDirectory() + "output_events.xml.gz";

		// --- FACILITY-BASED LISTENER (REQUIRED FOR personBased = false) ---
		FacilityResultsComparator listener = new FacilityResultsComparator();

		AccessibilityFromEvents.Builder builder =
			new AccessibilityFromEvents.Builder(scenario, eventsFile);
		builder.addDataListener(listener);
		builder.build().run();

		Map<Tuple<ActivityFacility, Double>, Map<String, Double>> facilityAcc =
			listener.getAccessibilitiesMap();

		// --- FIND THE FACILITY CORRESPONDING TO THE PERSONS' CELL ---
		// We find whichever facility lies at the same coordinate as (100,100) grid cell.
		// Because both persons are at same coord → they must map to same tile → same facility.
		Map<String, Double> firstAccPerCoord = new HashMap<>();
		for (Map.Entry<Tuple<ActivityFacility, Double>, Map<String, Double>> e : facilityAcc.entrySet()) {
			ActivityFacility fac = e.getKey().getFirst();
			Double time = e.getKey().getSecond();
			Double carAcc = e.getValue().get("car");

			String coordKey = fac.getCoord().getX() + "_" + fac.getCoord().getY() + "_" + time;

			if (!firstAccPerCoord.containsKey(coordKey)) {
				firstAccPerCoord.put(coordKey, carAcc);
			} else {
				double ref = firstAccPerCoord.get(coordKey);
				Assertions.assertEquals(ref, carAcc, EPS,
					"All facilities at the same coordinate and time must have the same car accessibility when person-based is OFF");
			}
		}
	}

	private static void addPerson(Scenario scenario,  String personId, int age, double income, String economic_status, double homeX, double homeY, Boolean restricted_mobility, String sex) {
		Person person = scenario.getPopulation().getFactory().createPerson(Id.createPersonId("testPerson_" + personId));
		person.getAttributes().putAttribute("age", age);
		person.getAttributes().putAttribute("income", income); //todo added income to test !!!
		person.getAttributes().putAttribute("economic_status", economic_status); // range: very_low, low, medium, high, very_high
		person.getAttributes().putAttribute("homeX", homeX);
		person.getAttributes().putAttribute("homeY", homeY);
		person.getAttributes().putAttribute("restricted_mobility", restricted_mobility); //todo
		person.getAttributes().putAttribute("sex", sex); //todo
		Plan plan = scenario.getPopulation().getFactory().createPlan();
		plan.addActivity(
			scenario.getPopulation().getFactory().createActivityFromCoord("home", new Coord(homeX, homeY)));

		person.addPlan(plan);
		person.setSelectedPlan(plan);
		scenario.getPopulation().addPerson(person);
	}

	private class PersonBasedResultsComparator implements PersonDataExchangeInterface {
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

	private class FacilityResultsComparator implements FacilityDataExchangeInterface {

		private final Map<Tuple<ActivityFacility, Double>, Map<String, Double>> accessibilitiesMap = new HashMap<>();

		@Override
		public synchronized void setFacilityAccessibilities(
			ActivityFacility facility,
			Double timeOfDay,
			String mode,
			double accessibility
		) {
			accessibilitiesMap
				.computeIfAbsent(new Tuple<>(facility, timeOfDay), k -> new HashMap<>())
				.put(mode, accessibility);
		}

		public Map<Tuple<ActivityFacility, Double>, Map<String, Double>> getAccessibilitiesMap() {
			return accessibilitiesMap;
		}

		@Override
		public void finish() {}
	}

}
