package com.mostc.pftt.scenario;

import com.mostc.pftt.host.Host;
import com.mostc.pftt.model.core.PhpBuild;
import com.mostc.pftt.results.ConsoleManager;

public class PhpUnitReflectionOnlyScenario extends AbstractPhpUnitReflectionScenario {

	@Override
	public IScenarioSetup setup(ConsoleManager cm, Host host, PhpBuild build, ScenarioSet scenario_set) {
		return SETUP_SUCCESS;
	}

	@Override
	public String getName() {
		return "PhpUnit-Reflection-Only";
	}

	@Override
	public boolean isImplemented() {
		return true;
	}

}
