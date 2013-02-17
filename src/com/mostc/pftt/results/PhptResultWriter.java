package com.mostc.pftt.results;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;

import org.kxml2.io.KXmlSerializer;

import com.github.mattficken.io.StringUtil;
import com.mostc.pftt.host.AHost;
import com.mostc.pftt.model.core.EPhptSection;
import com.mostc.pftt.model.core.EPhptTestStatus;
import com.mostc.pftt.results.ConsoleManager.EPrintType;
import com.mostc.pftt.scenario.ScenarioSet;

public class PhptResultWriter {
	protected final File dir;
	protected final HashMap<EPhptTestStatus,StatusListEntry> status_list_map;
	protected final KXmlSerializer serial;
	
	public PhptResultWriter(File dir) throws IOException {
		this.dir = dir;
		
		dir.mkdirs();
		
		status_list_map = new HashMap<EPhptTestStatus,StatusListEntry>();
		serial  = new KXmlSerializer();
		// setup serializer to indent XML (pretty print) so its easy for people to read
		serial.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
		
		for(EPhptTestStatus status:EPhptTestStatus.values())
			status_list_map.put(status, new StatusListEntry(status));
	}
	
	protected class StatusListEntry {
		protected final EPhptTestStatus status;
		protected final File journal_file;
		protected final PrintWriter journal_writer;
		protected final LinkedList<String> test_names;
		
		public StatusListEntry(EPhptTestStatus status) throws IOException {
			this.status = status;
			
			journal_file = new File(dir+"/"+status+".journal.txt");
			journal_writer = new PrintWriter(new FileWriter(journal_file));
			test_names = new LinkedList<String>();
		}
		
		public void write(PhptTestResult result) {
			final String test_name = result.test_case.getName();
			
			journal_writer.println(test_name);
			
			test_names.add(test_name);
		}
		public void close() throws IOException {
			journal_writer.close();
			
			// sort alphabetically
			Collections.sort(test_names);
			
			PrintWriter pw = new PrintWriter(new FileWriter(new File(dir+"/"+status+".txt")));
			switch(status) {
			case XSKIP:
			case UNSUPPORTED:
			case BORK:
				// this is a list/status that PHP run-tests doesn't produce, so its less likely that 
				// someone will want to pass this list to run-tests, so its safe to add a comment/header to the XSKIP list
				pw.println("; can add comments or comment out any line by adding ; or #");
				pw.println("; line will be ignored when you pass this list to pftt phpt_list");
				break;
			default:
				break;
			} // end switch
			for ( String test_name : test_names )
				pw.println(test_name);
			pw.close();
			
			// if here, collecting the results and writing them in sorted-order has worked ... 
			//   don't need journal anymore (pftt didn't crash, fail, etc...)
			journal_file.delete();
		}
	} // end protected class StatusListEntry

	public void close() throws IOException {
		// write tally file with 
		try {
			/*PhptTallyFile tally = new PhptTallyFile();
			tally.sapi_scenario_name = ScenarioSet.getSAPIScenario(scenario_set).getName();
			tally.build_branch = build.getVersionBranch(cm, host)+"";
			tally.test_pack_branch = test_pack.getVersionBranch()+"";
			tally.build_revision = build.getVersionString(cm, host);
			tally.test_pack_revision = test_pack.getVersion();
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
			tally.exception = counts.get(EPhptTestStatus.TEST_EXCEPTION).get();*/		
			
			/* TODO FileWriter fw = new FileWriter(new File(telem_dir, "tally.xml"));
			PhptTallyFile.write(tally, fw);
			fw.close();*/
		} catch ( Exception ex ) {
			ex.printStackTrace();
		}
		
		for ( StatusListEntry e : status_list_map.values() )
			e.close();
	} // end public void close
	
	protected void handleResult(ConsoleManager cm, AHost host, ScenarioSet scenario_set, PhptTestResult result) {
		status_list_map.get(result.status).write(result);
	
		
		final String test_case_base_name = result.test_case.getBaseName();
		
		final boolean store_all = PhptTestResult.shouldStoreAllInfo(result.status);
		
		
		//
		if (store_all || !cm.isNoResultFileForPassSkipXSkip()) {
			// may want to skip storing result files for PASS, SKIP or XSKIP tests
			try {
				File result_file = new File(dir, test_case_base_name+".xml");
				
				result_file.getParentFile().mkdirs();
				
				OutputStream out = new BufferedOutputStream(new FileOutputStream(result_file));
				
				serial.setOutput(out, null);
				
				// write result info in XML format
				serial.startDocument(null, null);
				// write result and reference to the XSL stylesheet
				result.serialize(serial, store_all, StringUtil.repeat("../", AHost.countUp(test_case_base_name, dir.getAbsolutePath()))+"/phptresult.xsl");
				serial.endDocument();
				
				serial.flush();
				out.close();
				
			} catch ( Exception ex ) {
				cm.addGlobalException(EPrintType.OPERATION_FAILED_CONTINUING, getClass(), "handleResult", ex, "", dir, test_case_base_name);
			}
		}
		//
		
		//
		if (store_all && StringUtil.isNotEmpty(result.shell_script)) {
			// store .cmd|.sh and .php file
			// (if no .cmd|.sh don't need a .php file; .php file needed for .cmd|.sh)
			String file_str = result.test_case.get(EPhptSection.FILE);
			if (StringUtil.isNotEmpty(file_str)) {
				FileWriter fw;
				
				try {
					fw = new FileWriter(host.joinIntoOnePath(dir.getAbsolutePath(), test_case_base_name+(host.isWindows()?".cmd":".sh")));
					fw.write(result.shell_script);
					fw.close();
				} catch ( Exception ex ) {
					cm.addGlobalException(EPrintType.OPERATION_FAILED_CONTINUING, getClass(), "handleResult", ex, "", dir, test_case_base_name);
				}
				
				try {
					fw = new FileWriter(host.joinIntoOnePath(dir.getAbsolutePath(), test_case_base_name+".php"));
					fw.write(file_str);
					fw.close();
				} catch ( Exception ex ) {
					cm.addGlobalException(EPrintType.OPERATION_FAILED_CONTINUING, getClass(), "handleResult", ex, "", dir, test_case_base_name);
				}
			}
		}
		//
	} // end protected void handleResult
	
} // end public class PhptResultWriter