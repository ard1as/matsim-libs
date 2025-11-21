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
import org.matsim.testcases.MatsimTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.matsim.contrib.accessibility.run.TinyAccessibilityTest.createTestScenario;


// STEP 1: make accessibility calculation depend on home location, but not on person attributes
public class PersonBasedAccessibilityTest {

	private static final Logger LOG = LogManager.getLogger(PersonBasedAccessibilityTest.class);

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

	//todo implement tests for other modes
	@Test
	void testCarAccessibility(){

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
}
