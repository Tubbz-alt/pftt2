package com.mostc.pftt.telemetry;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.mostc.pftt.host.Host;
import com.mostc.pftt.model.phpt.EBuildBranch;
import com.mostc.pftt.model.phpt.EPhptTestStatus;
import com.mostc.pftt.model.phpt.PhpBuild;
import com.mostc.pftt.model.phpt.PhptTestCase;
import com.mostc.pftt.model.phpt.PhptTestPack;
import com.mostc.pftt.scenario.ScenarioSet;
import com.mostc.pftt.ui.PhptDebuggerFrame;
import com.mostc.pftt.util.ErrorUtil;

/** Writes the telemetry during a test run.
 * 
 * @see PhptTelemetryReader
 * @author Matt Ficken
 *
 */

public class PhptTelemetryWriter extends PhptTelemetry {
	public File telem_dir; // XXX
	protected HashMap<EPhptTestStatus,PrintWriter> status_list_map;
	protected Host host;
	protected PrintWriter exception_writer;
	protected int total_count = 0;
	public PhptDebuggerFrame gui; // XXX
	protected HashMap<EPhptTestStatus,AtomicInteger> counts;
	protected PhpBuild build;
	protected PhptTestPack test_pack;
	protected ScenarioSet scenario_set;
	
	public PhptTelemetryWriter(Host host, PhptDebuggerFrame gui, File telem_base_dir, PhpBuild build, PhptTestPack test_pack, ScenarioSet scenario_set) throws IOException {
		super(host);
		this.host = host;
		this.gui = gui;
		this.scenario_set = scenario_set;
		this.build = build;
		this.test_pack = test_pack;
		this.telem_dir = new File(telem_base_dir + "/PHP-telemetry-"+System.currentTimeMillis());
		this.telem_dir.mkdirs();
		this.telem_dir = new File(this.telem_dir.getAbsolutePath());
		
		counts = new HashMap<EPhptTestStatus,AtomicInteger>();
		for (EPhptTestStatus status:EPhptTestStatus.values())
			counts.put(status, new AtomicInteger(0));
		
		exception_writer = new PrintWriter(new FileWriter(this.telem_dir+"/EXCEPTIONS.txt"));
		
		status_list_map = new HashMap<EPhptTestStatus,PrintWriter>();
		for(EPhptTestStatus status:EPhptTestStatus.values()) {
			FileWriter fw = new FileWriter(telem_dir+"/"+status+".txt");
			PrintWriter pw = new PrintWriter(fw);
			if (status==EPhptTestStatus.XSKIP) {
				// this is a list/status that PHP run-tests doesn't produce, so its less likely that 
				// someone will want to pass this list to run-tests, so its safe to add a comment/header to the XSKIP list
				pw.println("; can add comments or comment out any line by adding ; or #");
				pw.println("; line will be ignored when you pass this list to pftt phpt_list");
			}
			status_list_map.put(status, pw);
		}
	}
	
	@Override
	public void close() {
		for(EPhptTestStatus status:EPhptTestStatus.values()) {
			PrintWriter pw = status_list_map.get(status);
			pw.close();
		}
		
		// write tally file with 
		try {
			PhptTallyFile tally = new PhptTallyFile();
			tally.sapi_scenario_name = ScenarioSet.getSAPIScenario(scenario_set).getName();
			tally.build_branch = build.getVersionBranch(host)+"";
			tally.test_pack_branch = build.getVersionBranch(host)+""; // TODO test_pack.getBranch
			tally.build_revision = build.getVersionString(host);//
			tally.test_pack_revision = build.getVersionString(host); // TODO get test_pack version
			tally.os_name = host.getOSName();
			tally.os_name_long = host.getOSNameLong();
			tally.pass = counts.get(EPhptTestStatus.PASS).get();
			tally.fail = counts.get(EPhptTestStatus.FAIL).get();
			tally.skip = counts.get(EPhptTestStatus.SKIP).get();
			tally.xskip = counts.get(EPhptTestStatus.XSKIP).get();
			tally.xfail = counts.get(EPhptTestStatus.XFAIL).get();
			tally.xfail_works = counts.get(EPhptTestStatus.XFAIL_WORKS).get();
			tally.unsupported = counts.get(EPhptTestStatus.UNSUPPORTED).get();
			tally.bork = counts.get(EPhptTestStatus.BORK).get();
			tally.exception = counts.get(EPhptTestStatus.EXCEPTION).get();		
			FileWriter fw = new FileWriter(new File(telem_dir, "tally.xml"));
			PhptTallyFile.write(tally, fw);
			fw.close();
		} catch ( Exception ex ) {
			ex.printStackTrace();
		}
	}
	@Override
	public String getSAPIScenarioName() {
		return null;
	}
	@Override
	public String getBuildVersion() {
		return null;
	}
	@Override
	public EBuildBranch getBuildBranch() {
		return null;
	}
	@Override
	public String getTestPackVersion() {
		return null;
	}
	@Override
	public EBuildBranch getTestPackBranch() {
		return null;	
	}
	@Override
	public List<String> getTestNames(EPhptTestStatus status) {
		return null;
	}
	@Override
	public String getOSName() {
		return null;
	}
	@Override
	public int count(EPhptTestStatus status) {
		return 0;
	}
	@Override
	public float passRate() {
		return 0;
	}
	
	public void show_exception(PhptTestCase test_file, Throwable ex) {
		show_exception(test_file, ex, null);
	}
	public void show_exception(PhptTestCase test_file, Throwable ex, Object a) {
		show_exception(test_file, ex, a, null);
	}
	protected void writeException(PhptTestCase test_case, Throwable ex) {
		ex.printStackTrace();
		
		// store all stack traces in 1 file
		synchronized(exception_writer) {
			exception_writer.println("EXCEPTION "+test_case);
			ex.printStackTrace(exception_writer);
			exception_writer.flush(); // CRITICAL
		}
	}
	public void show_exception(PhptTestCase test_case, Throwable ex, Object a, Object b) {
		writeException(test_case, ex);
		
		String ex_str = ErrorUtil.toString(ex);
		
		// count exceptions as a result (the worst kind of failure, a pftt failure)
		addResult(new PhptTestResult(host, EPhptTestStatus.EXCEPTION, test_case, ex_str, null, null, null, null, null, null, null, null, null, null));
	}
	int completed = 0; // XXX
	public void addResult(PhptTestResult result) {
		// FUTURE enqueue in writer thread to avoid slowing down PhptThreads
		// also, on Windows, enqueue printing to console here
		//      -on Windows, if you grab the scrollbar, it will pause printing and
		//       println() call(s) will be blocked until scrollbar is released (ie this can pause/block up all the testing)
		
		counts.get(result.status).incrementAndGet();
		
		if (gui!=null) {
			// show in gui (if open)
			gui.showResult(host, getTotalCount(), completed, result);	
		}		
		
		// record in list files
		PrintWriter pw = status_list_map.get(result.status);
		pw.println(result.test_case.getName());
		
		// have result store diff, output, expected as appropriate
		try {
			result.write(telem_dir);
		} catch ( Exception ex ) {
			writeException(result.test_case, ex);
		}
		
		// show on console
		System.out.println(result.status+" "+result.test_case);
	}

	@Override
	public int getTotalCount() {
		return total_count;
	}

	public void setTotalCount(int total_count) {
		this.total_count = total_count;
	}
		
} // end public class PhptTelemetryWriter
