@echo off
REM script for running PFTT on Windows

set PFTT_HOME=C:\php-sdk\PFTT\current

set CLASSPATH=%PFTT_HOME%\build;%PFTT_HOME%\lib\groovy-1.8.6.jar;%PFTT_HOME%\lib\icu4j-49_1.jar;%PFTT_HOME%\lib\icudata.jar;%PFTT_HOME%\lib\icutzdata.jar;%PFTT_HOME%\lib\j2ssh-common-0.2.9.jar;%PFTT_HOME%\lib\j2ssh-core-0.2.9.jar;%PFTT_HOME%\lib\jansi-1.7.jar;%PFTT_HOME%\lib\jline-0.9.94.jar;%PFTT_HOME%\lib\jzlib-1.0.7.jar;%PFTT_HOME%\lib\selenium-server-standalone-2.19.0.jar;%PFTT_HOME%\lib\xercesImpl.jar;%PFTT_HOME%\lib\xmlpull-1.1.3.1.jar;%PFTT_HOME%\lib\commons-net-3.1.jar;%PFTT_HOME%\lib\commons-cli-1.2.jar;%PFTT_HOME%\lib\antlr-2.7.7.jar;%PFTT_HOME%\lib\asm-3.2.jar;%PFTT_HOME%\lib\asm-analysis-3.2.jar;%PFTT_HOME%\lib\asm-commons-3.2.jar;%PFTT_HOME%\lib\asm-tree-3.2.jar;%PFTT_HOME%\lib\asm-util-3.2.jar

REM find java and execute PfttMain
REM search %PATH% for java
WHERE java > NUL
if %ERRORLEVEL% EQU 0 (
	java -classpath %CLASSPATH% com.mostc.pftt.main.PfttMain %*
) ELSE (
	REM check for JAVA_HOME
	IF EXIST %JAVA_HOME%\lib\tools.jar (
		%JAVA_HOME%\bin\java -classpath %CLASSPATH% com.mostc.pftt.main.PfttMain %*
	) ELSE (
		ECHO user error set JAVA_HOME or add java to PATH and try again
	)
)