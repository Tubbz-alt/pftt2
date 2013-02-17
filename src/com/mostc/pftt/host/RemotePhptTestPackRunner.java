package com.mostc.pftt.host;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;

import com.github.mattficken.io.StringUtil;
import com.mostc.pftt.model.core.PhpBuild;
import com.mostc.pftt.model.core.PhptActiveTestPack;
import com.mostc.pftt.model.core.PhptSourceTestPack;
import com.mostc.pftt.model.core.PhptTestCase;
import com.mostc.pftt.results.LocalConsoleManager;
import com.mostc.pftt.results.PhpResultPackWriter;
import com.mostc.pftt.scenario.ScenarioSet;

/**
 * 
 * @see PSCAgentServer - server side
 *
 */

public class RemotePhptTestPackRunner extends AbstractRemoteTestPackRunner<PhptActiveTestPack, PhptSourceTestPack, PhptTestCase> {
	
	public RemotePhptTestPackRunner(PhpResultPackWriter tmgr, ScenarioSet scenario_set, PhpBuild build, LocalHost host, AHost remote_host) {
		super(tmgr, scenario_set, build, host, remote_host);
	}

	@Override
	protected void generateSimulation(PhptSourceTestPack test_pack) throws FileNotFoundException, IOException, Exception {
		final PrintStream orig_ps = System.out;
		ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
		try {
			System.setOut(new PrintStream(out));
			
			runAllTests(test_pack);
		} finally {
			System.setOut(orig_ps);
		}
		
		System.out.println(StringUtil.toJava(out.toString()));
	}
	
	@Override
	protected void simulate() {
		String str = 
				"";
		System.setIn(new ByteArrayInputStream(str.getBytes()));
	}
	
	public static void main(String[] args) throws Exception {
		PhpBuild build = new PhpBuild("C:\\php-sdk\\php-5.5-ts-windows-vc9-x86-re6bde1f");
		
		ScenarioSet scenario_set = ScenarioSet.getDefaultScenarioSets().get(0);
		
		LocalHost host = new LocalHost();
		
		LocalConsoleManager cm = new LocalConsoleManager(null, null, false, false, false, false, true, false, true, false, false, false, 1, true, 1, 1, 1, null, null, null, null);
		
		PhptSourceTestPack test_pack = new PhptSourceTestPack("C:\\php-sdk\\php-test-pack-5.5-nts-windows-vc9-x86-re6bde1f");
		test_pack.open(cm, host);
		
		PhpResultPackWriter tmgr = new PhpResultPackWriter(host, cm, new File(host.getPhpSdkDir()), build, scenario_set);
				
		RemotePhptTestPackRunner runner = new RemotePhptTestPackRunner(tmgr, scenario_set, build, host, host);
		if (args.length>0) {
			if (args[0].equals("simulate")) {
				//
				runner.simulate();
			} else if (args[0].equals("generate")) {
				//
				runner.generateSimulation(test_pack);
			}
		}
	} // end public static void main
	
} // end public class RemotePhptTestPackRunner