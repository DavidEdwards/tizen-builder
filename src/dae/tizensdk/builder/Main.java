package dae.tizensdk.builder;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.commons.io.FileUtils;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import com.yahoo.platform.yui.compressor.YUICompressor;

/**
 * <p>A simple builder for the Tizen SDK, that packages may be built quickly for faster App testing.</p>
 * 
 * <p>For optimum power, include this in your automated build process in Tizen SDK (Eclipse)</p>
 * 
 * @author David Edwards <knossos@gmail.com>
 *
 */
public class Main {
    @Option(name="-i",usage="input folder of your Tizen SDK Project")
    private File projectFolder;

    @Option(name="-w",usage="Tizen SDK (Eclipse) workspace location")
    private File workspaceFolder = new File(".");

    @Option(name="-O",usage="output folder")
    private File outputFolder = new File(".");

    @Option(name="-t",usage="tizen cli - tizen-sdk/tools/ide/bin (tizen)")
    private File tizenCli = new File("tizen");
    
    @Option(name="-p",usage="location of profiles.xml - contains security certificate information (defaults to within your Tizen SDK workspace, -P to see contents)")
    private String profilesLocation = null;

    @Option(name="-n",usage="your profile name - is retrieved from profiles.xml (default)")
    private String profileName = "default";

    @Option(name="-P",usage="print out profiles.xml to stdout (requires -p)")
    private boolean displayProfilesContents;

    @Option(name="-V",usage="should we output verbose information to stdout")
    private boolean verboseMode;

    @Option(name="-o",usage="should we obfuscate")
    private boolean obfuscate;
	

    @Argument
    private List<String> arguments = new ArrayList<String>();

	public static void main(String[] args) {
		new Main(args);
	}
	
	public Main(String[] args) {
		CmdLineParser parser = new CmdLineParser(this);
        
        try {
            parser.parseArgument(args);
        } catch( CmdLineException e ) {
            System.err.println(e.getMessage());
            System.err.println("java -jar sdkb.jar [options...] arguments...");
            parser.printUsage(System.err);
            System.err.println();

            return;
        }
        
        // Validate the profiles.xml file
        File profilesFile = null;
        if(profilesLocation != null) {
    		if(profilesLocation.contains("!WORKSPACE!")) {
    			if(workspaceFolder == null || !workspaceFolder.exists() || !workspaceFolder.isDirectory()) {
    				System.err.println("Workspace was given as a substition in profiles, but the workspace option is not set correctly \""+workspaceFolder.getAbsolutePath()+"\" (-w).");
    				return;
    			} else {
    				profilesLocation = profilesLocation.replaceAll("!WORKSPACE!", workspaceFolder.getAbsolutePath());
    			}
    		}
    		
    		profilesFile = new File(profilesLocation);
			if(!profilesFile.exists() || !profilesFile.isDirectory()) {
				System.err.println("Profiles location is not valid \""+profilesFile.getAbsolutePath()+"\" (-p).");
				return;
			}
    	} else {
			profilesFile = new File(workspaceFolder, ".metadata/.plugins/org.tizen.common.sign/profiles.xml");
    	}
        
        // If we only want to display the profiles.xml file
        if(displayProfilesContents) {
            if(profilesFile == null || !profilesFile.exists() || !profilesFile.isFile()) {
            	System.err.println("The argument -i <PATH> must be provided, it must be a directory, and it must be the path of your Samsung SDK Project.");
            	return;
            }
            
			try {
				String data = FileUtils.readFileToString(profilesFile);
	            System.out.println("Your profiles.xml file:");
	            System.out.println(data);
			}
			catch (IOException e) {
				e.printStackTrace();
			}
        	
        	return;
        }

        // Check that the SDK Project folder exists and is valid
        if(projectFolder == null || !projectFolder.exists() || !projectFolder.isDirectory()) {
        	if(verboseMode) System.err.println("The argument -i <PATH> must be provided, it must be a directory, and it must be the path of your Samsung SDK Project.");
        	return;
        }
        
        // Generate a random ID for the temporary folder and attempt to create it
        int randomId = (new Random().nextInt(99999999));
        File tempDirectory = new File("temp-builder-folder-"+randomId);
        if(!tempDirectory.mkdirs()) {
        	if(verboseMode) System.err.println("Failed to create a temporary directory for storing build files ("+tempDirectory.getAbsolutePath()+").");
        	return;
        }
        
        try {
            if(verboseMode) System.out.println("Project folder: "+projectFolder.getAbsolutePath());
            if(verboseMode) System.out.println("Temporary folder: "+tempDirectory.getAbsolutePath());
            
            if(verboseMode) System.out.println("Copying project folder to temporary directory.");
			FileUtils.copyDirectory(projectFolder, tempDirectory, new FileFilter() {
				@Override
				public boolean accept(File file) {
					if(file.getName().endsWith(".jar")) return false;
					
					return true;
				}
			});
//            if(verboseMode) System.out.println("Deleting non-required files.");
//			FileUtils.deleteQuietly(new File(tempDirectory, ".project"));
//			FileUtils.deleteQuietly(new File(tempDirectory, ".settings"));
			
			if(obfuscate) {
	            if(verboseMode) System.out.println("Obfuscation option set. Traversing temporary folder.");
				obfuscateJavascript(tempDirectory);
			}

            if(verboseMode) System.out.println("Building package.");
            
            String commandToTizen = "tizen.bat package -t wgt -s "+profileName+" -- \""+tempDirectory.getAbsolutePath()+"\"";
            
            ProcessBuilder pb = new ProcessBuilder(commandToTizen.split(" "));
            Process p = pb.start();
            try {
				p.waitFor();
			}
			catch (InterruptedException e) {
			}
            
            File outputFile = null;
            File[] tempList = tempDirectory.listFiles();
            for(File temp : tempList) {
            	if(temp.getName().endsWith(".wgt")) {
            		outputFile = new File(outputFolder, temp.getName());
            		FileUtils.copyFile(temp, outputFile);
            		break;
            	}
            }
            
            if(outputFile == null) {
            	System.err.println("The App Package was not created - unknown reason - check that your tizen cli file is correctly set (-t)!");
            	return;
            } else {
            	if(verboseMode) System.out.println("App Package created at: "+outputFile.getAbsolutePath());
            	File projectAppPackage = new File(projectFolder, outputFile.getName());
        		FileUtils.copyFile(outputFile, projectAppPackage);
            	if(verboseMode) System.out.println("Copied to: "+projectAppPackage.getAbsolutePath());
            }
			
            if(verboseMode) System.out.println("Package is built.");
		} 
        catch (IOException e) {
	        System.err.println("Could not build package!");
			e.printStackTrace();
			return;
		} finally {
            if(verboseMode) System.out.println("Deleting temporary directory.");
	        FileUtils.deleteQuietly(tempDirectory);
		}
        
        if(verboseMode) System.out.println("Build process completed.");
	}
	
	private void obfuscateJavascript(File node) {
        if (node.isDirectory()) {
        	for(File file : node.listFiles()) {
        		obfuscateJavascript(file);
        	}
        } else {
        	// Only obfuscate JS files, and only if they aren't already minified, and don't obfuscate the Samsung Caph library
        	if(node.getName().endsWith(".js") && !node.getName().contains(".min") && !node.getAbsolutePath().contains("lib"+File.separator+"caph")) {
	        	if(verboseMode) System.out.println("Obfuscate: "+node.getAbsolutePath());
				
				File generated = new File(node.getName()+".min.js");
			    YUICompressor.main(new String[] {
			    		node.getAbsolutePath(), 
						"-o",
						generated.getName()
				});
			    
			    try {
			    	FileUtils.deleteQuietly(node);
//		        	if(verboseMode) System.out.println("Overwriting \""+node.getAbsolutePath()+"\" with \""+generated.getAbsolutePath()+"\".");
					FileUtils.moveFile(generated, node);
				}
				catch (IOException e) {
		        	if(verboseMode) System.out.println("Could not overwrite javascript file with generated obfuscated file.");
					e.printStackTrace();
				}
        	}
        }
	}

}
