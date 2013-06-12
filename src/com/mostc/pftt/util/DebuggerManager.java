package com.mostc.pftt.util;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import com.github.mattficken.io.StringUtil;
import com.mostc.pftt.host.AHost;
import com.mostc.pftt.host.ExecOutput;
import com.mostc.pftt.host.AHost.ExecHandle;
import com.mostc.pftt.host.IProgramRunner;
import com.mostc.pftt.host.LocalHost.LocalExecHandle;
import com.mostc.pftt.model.core.PhpBuild;
import com.mostc.pftt.model.core.PhptTestCase;
import com.mostc.pftt.results.ConsoleManager;
import com.mostc.pftt.results.EPrintType;
import com.mostc.pftt.runner.AbstractTestPackRunner.TestPackRunnerThread;
import com.mostc.pftt.scenario.Scenario;
import com.mostc.pftt.scenario.ScenarioSet;

/** handles using a debugger on a build that has a test case run against it
 * 
 * @author Matt Ficken
 *
 */

// XXX support for GDB on Linux
public abstract class DebuggerManager {
	protected String src_path, debug_path;
	
	/** provides a new debugger for the running instance of build/process represented by handle, or 
	 * returns null if debugger could not be created
	 * 
	 * @param cm
	 * @param host
	 * @param scenario_set
	 * @param server_name
	 * @param build
	 * @param handle
	 * @return
	 */
	public Debugger newDebugger(ConsoleManager cm, AHost host, ScenarioSet scenario_set, Object server_name, PhpBuild build, LocalExecHandle handle) {
		int pid = handle.getProcessID();
		if (pid<1)
			return null;
		
		return newDebugger(cm, host, scenario_set, server_name, build, pid, handle);
	}
	
	/** provides a new debugger for the running process (identified by process_id) or returns
	 * null if debugger could not be created
	 * 
	 * @param cm
	 * @param host
	 * @param scenario_set TODO
	 * @param server_name
	 * @param build
	 * @param process_id
	 * @param process TODO
	 * @return
	 */
	public abstract Debugger newDebugger(ConsoleManager cm, AHost host, ScenarioSet scenario_set, Object server_name, PhpBuild build, int process_id, ExecHandle process);
	
	/** gets debug paths from scenarios...
	 * 
	 * for instance, this lets apache builds provide debug symbols to WinDebug
	 * 
	 * @param cm
	 * @param host
	 * @param build
	 * @param scenario_set
	 */
	protected void addToDebugPathFromScenarios(ConsoleManager cm, AHost host, PhpBuild build, ScenarioSet scenario_set) {
		ArrayList<String> paths = new ArrayList<String>(5);
		for ( Scenario s : scenario_set ) {
			s.addToDebugPath(cm, host, build, paths);
		}
		if (this.debug_path==null)
			this.debug_path = "";
		for ( String path : paths ) {
			if (this.debug_path.length()>0)
				this.debug_path += host.dirSeparator();
			this.debug_path += path;
		}
	}
	
	/** guesses the source pack and debug pack locations based on build, unless
	 * -src_pack and -debug_pack console options are given 
	 * 
	 * @param cm
	 * @param host
	 * @param build
	 */
	private PhpBuild found_src_debug_pack_build;
	protected void ensureFindSourceAndDebugPack(ConsoleManager cm, AHost host, ScenarioSet scenario_set, PhpBuild build) {
		if (build==found_src_debug_pack_build)
			return;
		this.found_src_debug_pack_build = build; // cache
		if (!host.isWindows()) {
			// only PHP on Windows has standard conventions for naming/locating source and debug packs
			this.src_path = cm.getSourcePack();
			this.debug_path = cm.getDebugPack().getPath();
			addToDebugPathFromScenarios(cm, host, build, scenario_set);
			return;
		}
		
		// use any source and debug packs given on command line
		String def_source_path = cm.getSourcePack();
		String def_debug_path = cm.getDebugPack().getPath();
		
		// (in addition to )guessing the source pack and debug pack from the build (PHP-on-Windows follows conventions that allow this)
		try {
			if (StringUtil.isEmpty(def_source_path))
				this.src_path = build.guessSourcePackPath(cm, host);
			else
				this.src_path = host.joinMultiplePaths(def_source_path, build.guessSourcePackPath(cm, host));
		} catch ( Exception ex ) {
			cm.addGlobalException(EPrintType.CANT_CONTINUE, getClass(), "ensureFindSourceAndDebugPack", ex, "");
		}
		try {
			if (StringUtil.isEmpty(def_debug_path))
				this.debug_path = build.guessDebugPackPath(cm, host);
			else
				this.debug_path = host.joinMultiplePaths(def_debug_path, build.guessDebugPackPath(cm, host));
		} catch ( Exception ex ) {
			cm.addGlobalException(EPrintType.CANT_CONTINUE, getClass(), "ensureFindSourceAndDebugPack", ex, "");
		}
		
		addToDebugPathFromScenarios(cm, host, build, scenario_set);
	} // end protected void ensureFindSourceAndDebugPack
	
	/** turns generic objects into server name to give to debugger.
	 * 
	 * this focuses primarily on PhptTestCases.
	 * 
	 * @param input
	 * @return
	 */
	public static String toServerName(Object input) {
		String str = null;
		if (input instanceof Object[]) {
			StringBuilder sb = new StringBuilder();
			Object[] array = (Object[]) input;
			// read in reverse order since the last element is probably the most important (most likely to be crashing)
			for ( int i=array.length-1 ; i > -1 ; i++ ) {
				_toServerName(sb, array[i]);
				if (sb.length() > 100)
					break; // limit length
			}
			str = sb.toString();
		} else if (input instanceof Collection<?>) { // Collection List LinkedBlockingQueue
			@SuppressWarnings("unchecked")
			Collection<Object> c = (Collection<Object>) input;
			ArrayList<Object> list = new ArrayList<Object>(c.size());
			list.addAll(c);
			
			// read in reverse order since the last element is probably the most important (most likely to be crashing)
			Collections.reverse(list);
			StringBuilder sb = new StringBuilder();
			for ( Object a : list) {
				_toServerName(sb, a);
				if (sb.length() > 100)
					break; // limit length
			}
			str = sb.toString();
		} else if (input instanceof PhptTestCase) {
			// single test case - use whole test name (w/o .phpt)
			str = ((PhptTestCase)input).getBaseName();
		} else if (input!=null) {
			str = input.toString();
		}
		if (StringUtil.isEmpty(str))
			return "PFTT";
		else
			return str;
	}
	
	private static void _toServerName(StringBuilder sb, Object a) {
		if (a instanceof PhptTestCase) {
			PhptTestCase t = (PhptTestCase) a;
			if (sb.length() > 0)
				sb.append(' ');
			if (sb.indexOf(t.getFolder())!=-1)
				// with lots of tests running, name needs to be kept short - use short name instead of full name
				sb.append(t.getShortName());
			else
				sb.append(t.getBaseName());
		} else {
			sb.append(a.toString());
		}
	}

	public static abstract class Debugger implements IProgramRunner {

		public abstract void close(ConsoleManager cm);
		public abstract boolean isRunning();
		
		/*
		@Override
		public boolean exec(ConsoleManager cm, String ctx_str, String cmd, int timeout_sec, Map<String, String> env, byte[] stdin_post, Charset charset, String current_dir) throws IllegalStateException, Exception {
			return execOut(cmd, timeout_sec, env, stdin_post, charset, current_dir).printOutputIfCrash(ctx_str, cm).isSuccess();
		}
		@Override
		public boolean exec(ConsoleManager cm, String ctx_str, String commandline, int timeout, Map<String, String> env, byte[] stdin, Charset charset, String chdir, TestPackRunnerThread thread, int thread_slow_sec) throws Exception {
			return execOut(commandline, timeout, env, stdin, charset, chdir, thread, thread_slow_sec).printOutputIfCrash(ctx_str, cm).isSuccess();
		}
		public ExecHandle execThread(String commandline) throws Exception {
			return execThread(commandline, null, null, null);
		}
		public ExecHandle execThread(String commandline, byte[] stdin_data) throws Exception {
			return execThread(commandline, null, null, stdin_data);
		}
		public ExecHandle execThread(String commandline, String chdir) throws Exception {
			return execThread(commandline, null, chdir, null);
		}
		public ExecHandle execThread(String commandline, String chdir, byte[] stdin_data) throws Exception {
			return execThread(commandline, null, chdir, stdin_data);
		}
		public ExecHandle execThread(String commandline, Map<String,String> env, byte[] stdin_data) throws Exception {
			return execThread(commandline, env, null, stdin_data);
		}
		public ExecHandle execThread(String commandline, Map<String,String> env, String chdir) throws Exception {
			return execThread(commandline, env, chdir, null);
		}
		public ExecOutput execOut(String cmd, int timeout_sec, Map<String,String> object, byte[] stdin_post, Charset charset) throws IllegalStateException, Exception {
			return execOut(cmd, timeout_sec, object, stdin_post, charset, (String)null);
		}*/
		
	}
	
} // end public abstract class DebuggerManager
