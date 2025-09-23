package org.matsim.contrib.accessibility.run;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.contrib.accessibility.AccessibilityConfigGroup;
import org.matsim.contrib.accessibility.AccessibilityModule;
import org.matsim.contrib.accessibility.AccessibilityUtils;
import org.matsim.contrib.accessibility.Modes4Accessibility;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.scenario.ScenarioUtils;

import java.nio.file.Path;
import java.util.List;

public class RunPersonBasedAccBerlin {

	static String OUTPUT_DIR = "../public-svn/matsim/scenarios/countries/de/berlin/projects/fabilut/output-1pct/policy";
	private static final String crs = "EPSG:25832";

	private static final Logger LOG = LogManager.getLogger(RunPersonBasedAccBerlin.class);

	public static void main(String[] args) {
		LoadFiles();
	}

	private static void LoadFiles() {

//todo	== Input Files ==
		String configFile = Path.of(OUTPUT_DIR, "output_config.xml").toString();
		String networkFile = Path.of(OUTPUT_DIR, "output_network.xml.gz").toString();
//		String eventsFile = Path.of(OUTPUT_DIR, "output_events.xml.gz").toString();
		String plansFile = Path.of(OUTPUT_DIR, "output_plans.xml.gz").toString();

//todo	== Load config ==
		Config config = ConfigUtils.loadConfig(configFile, new AccessibilityConfigGroup());

//todo	== Adjust config ==
		config.controller().setLastIteration(0);
		config.controller().setOutputDirectory(OUTPUT_DIR);
		config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.overwriteExistingFiles);
		config.global().setCoordinateSystem(crs);
		config.routing().setRoutingRandomness(0.);

		config.network().setInputFile(networkFile);
		config.plans().setInputFile(plansFile);

//todo	== Accessibility config ==
		AccessibilityConfigGroup acg = ConfigUtils.addOrGetModule(config, AccessibilityConfigGroup.class);
		acg.setPersonBased(true);
		acg.setTileSize_m(100);
		acg.setAreaOfAccessibilityComputation(AccessibilityConfigGroup.AreaOfAccesssibilityComputation.fromPopulation);

//todo	== Mode selection ==
		List<Modes4Accessibility> accModes = List.of(Modes4Accessibility.teleportedWalk);
		for(Modes4Accessibility mode : Modes4Accessibility.values()) {
			acg.setComputingAccessibilityForMode(mode, accModes.contains(mode));
		}

//todo	== Load scenario ==
		final Scenario scenario = ScenarioUtils.loadScenario(config);

//todo	== Controller ==
		Controler controler = new Controler(scenario);

//todo	== Add accessibility module	==
		AccessibilityModule accessibilityModule = new AccessibilityModule();
		controler.addOverridingModule(accessibilityModule);

//todo	== Run ==
		LOG.info("Running person-based accessibility computation for Berlin...");
		controler.run();

	}

}
