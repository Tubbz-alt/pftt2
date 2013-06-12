
def describe() {
	"Load Drupal Application"
}

def scenarios() {
	new DrupalScenario()
}


/** see drupal.patch.txt for patch to drupal required to make this work
 *
 */
class DrupalPhpUnitTestPack extends PhpUnitSourceTestPack {
	
	@Override
	public String getNameAndVersionString() {
		return "Drupal-8.x-dev";
	}
	
	@Override
	protected String getSourceRoot(AHost host) {
		return host.getPfttDir()+"/cache/working/drupal-8.x-dev";
	}
	
	@Override
	public boolean isDevelopment() {
		return true;
	}
	
	@Override
	protected boolean isFileNameATest(String file_name) {
		// many apps/frameworks name their test files Test*.php.
		// wordpress does not ... check all .php files for PhpUnit test case classes.
		return file_name.endsWith(".php");
	}
	
 
	@Override
	protected boolean openAfterInstall(ConsoleManager cm, AHost host) throws Exception {
		// @see core\vendor\phpunit\phpunit\phpunit.xml.dist
		
		
		addPhpUnitDist(getRoot()+"/core/vendor/phpunit/phpunit/Tests", getRoot()+"/core/vendor/phpunit/phpunit/PHPUnit/Autoload.php");
		
		return true;
	} // end public boolean openAfterInstall
		
} // end class DrupalPhpUnitTestPack

def getPhpUnitSourceTestPack() {
	return new DrupalPhpUnitTestPack();
}
