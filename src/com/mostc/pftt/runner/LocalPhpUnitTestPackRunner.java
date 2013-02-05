package com.mostc.pftt.runner;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpVersion;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.params.SyncBasicHttpParams;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;

import com.mostc.pftt.host.AHost;
import com.mostc.pftt.model.app.PhpUnitActiveTestPack;
import com.mostc.pftt.model.app.PhpUnitSourceTestPack;
import com.mostc.pftt.model.app.PhpUnitTestCase;
import com.mostc.pftt.model.core.PhpBuild;
import com.mostc.pftt.model.sapi.ApacheManager;
import com.mostc.pftt.model.sapi.TestCaseGroupKey;
import com.mostc.pftt.results.ConsoleManager;
import com.mostc.pftt.results.ITestResultReceiver;
import com.mostc.pftt.scenario.AbstractFileSystemScenario.ITestPackStorageDir;
import com.mostc.pftt.scenario.ScenarioSet;

// TODO sapiscenario support for creating testcase runner
// TODO NTS testcase support
//     file: __CG__SymfonyBridgeDoctrineTestsFixturesCompositeIdentEntity.php
//     dir: symfony2_finder
//     database
// TODO result-pack support
// TODO when RESTARTING_AND_RETRYING, should report CRASH
public class LocalPhpUnitTestPackRunner extends AbstractLocalTestPackRunner<PhpUnitActiveTestPack, PhpUnitSourceTestPack, PhpUnitTestCase> {
	final Map<String,String> globals = new HashMap<String,String>();
	final Map<String, String> env = new HashMap<String,String>();
	final Map<String, String> constants = new HashMap<String,String>();
	final HttpParams params;
	final HttpProcessor httpproc;
	final HttpRequestExecutor httpexecutor;
	final ApacheManager smgr;
	
	public LocalPhpUnitTestPackRunner(ConsoleManager cm, ITestResultReceiver twriter, ScenarioSet scenario_set, PhpBuild build, AHost host) {
		super(cm, twriter, scenario_set, build, host);
		
		params = new SyncBasicHttpParams();
		HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
		HttpProtocolParams.setContentCharset(params, "UTF-8");
		HttpProtocolParams.setUserAgent(params, "Mozilla/5.0 (Windows NT 6.1; rv:12.0) Gecko/20120405 Firefox/14.0.1");
		HttpProtocolParams.setUseExpectContinue(params, true);
		
		httpproc = new ImmutableHttpProcessor(new HttpRequestInterceptor[] {
				// Required protocol interceptors
				new RequestContent(),
				new RequestTargetHost(),
				// Recommended protocol interceptors
				new RequestConnControl(),
				new RequestUserAgent(),
				new RequestExpectContinue()
			});
		
		httpexecutor = new HttpRequestExecutor();
		
		smgr = new ApacheManager();
	}

	@Override
	protected void setupStorageAndTestPack(ITestPackStorageDir storage_dir, List<PhpUnitTestCase> test_cases) {
		
	}
	
	@Override
	protected TestCaseGroupKey createGroupKey(PhpUnitTestCase test_case, TestCaseGroupKey group_key) throws Exception {
		return new TestCaseGroupKey(null, null);
	}

	@Override
	protected boolean handleNTS(TestCaseGroupKey group_key, PhpUnitTestCase test_case) {
		return false;
	}

	@Override
	protected PhpUnitThread createTestPackThread(boolean parallel) {
		return new PhpUnitThread(parallel);
	}
	
	public class PhpUnitThread extends TestPackThread<PhpUnitTestCase> {
		final String my_temp_dir;

		protected PhpUnitThread(boolean parallel) {
			super(parallel);
			my_temp_dir = runner_host.fixPath(runner_host.mktempname(runner_host.getPhpSdkDir()+"/temp/", getClass()) + "/");
		}

		@Override
		protected void runTest(TestCaseGroupKey group_key, PhpUnitTestCase test_case) throws IOException, Exception, Throwable {
			HttpPhpUnitTestCaseRunner r = new HttpPhpUnitTestCaseRunner(params, httpproc, httpexecutor, smgr, null, globals, env, cm, runner_host, scenario_set, build, test_case, my_temp_dir, constants, test_case.php_unit_dist.getIncludePath(), test_case.php_unit_dist.getIncludeFiles());
			//CliPhpUnitTestCaseRunner r = new CliPhpUnitTestCaseRunner(globals, env, cm, rt.host, scenario_set, build, test_case, temp_dir, constants, test_case.php_unit_dist.getIncludePath(), test_case.php_unit_dist.getIncludeFiles());
			r.runTest();
		}
		
	} // end public class PhpUnitThread

} // end public class LocalPhpUnitTestPackRunner
