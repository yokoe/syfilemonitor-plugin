package com.kreuz45.plugins;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import javax.servlet.ServletException;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Sample {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link HelloWorldBuilder} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields (like {@link #name})
 * to remember the configuration.
 *
 * <p>
 * When a build is performed, the {@link #perform(AbstractBuild, Launcher, BuildListener)}
 * method will be invoked. 
 *
 * @author Kohsuke Kawaguchi
 */
public class FileModificationCheckBuilder extends Builder {

    private final String input;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public FileModificationCheckBuilder(String input) {
        this.input = input;
    }

    /**
     * We'll use this from the <tt>config.jelly</tt>.
     */
    public String getInput() {
        return input;
    }
    
    private ArrayList<File> getFilesUnderDirectory(String rootDirectory) {
    	ArrayList<File> entries = new ArrayList<File>();
        File dir = new File(rootDirectory);
        File[] files = dir.listFiles();
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (file.isDirectory()) {
            	ArrayList<File> filesUnderSubDirectory = getFilesUnderDirectory(file.getAbsolutePath());
            	entries.addAll(filesUnderSubDirectory);
            } else {
            	entries.add(file);
            }
        }
    	return entries;
    }
    
    private HashMap<String, Long> getPreviousDirectoryState(AbstractBuild build, Launcher launcher, BuildListener listener) {
    	String previousStateString;
		try {
			previousStateString = build.getWorkspace().child("files.txt").readToString();
		} catch (IOException e) {
			e.printStackTrace();
			listener.getLogger().println("Previous directory state not found.");
			return new HashMap<String, Long>();
		}
    	JSONObject beforeJSON = JSONObject.fromObject(previousStateString).getJSONObject("files");
    	HashMap<String, Long> before = new HashMap<String, Long>();
    	@SuppressWarnings("unchecked")
		Iterator<String> it = beforeJSON.keys();
    	while(it.hasNext()) {
    		String key = it.next();
    		listener.getLogger().println(key);
    		before.put(key, beforeJSON.getLong(key));
    	}
    	return before;
    }
    private HashMap<String, Long> getCurrentDirectoryState(AbstractBuild build, Launcher launcher, BuildListener listener) {
    	ArrayList<File> files = getFilesUnderDirectory(input);
        HashMap<String, Long> fileDates = new HashMap<String, Long>(); 
        
        for (File file : files) {
        	fileDates.put(file.toString(), file.lastModified());
        }
        return fileDates;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        // This is where you 'build' the project.
        // Since this is a dummy, we just say 'hello world' and call that a build.
    	
    	// Load previous state
    	HashMap<String, Long> before = this.getPreviousDirectoryState(build, launcher, listener);
    	HashMap<String, Long> current = this.getCurrentDirectoryState(build, launcher, listener);
    	
    	ArrayList<String> addedFiles = new ArrayList<String>();
    	ArrayList<String> modifiedFiles = new ArrayList<String>();
    	ArrayList<String> deletedFiles = new ArrayList<String>();
    	
    	for(Entry<String, Long> entry : before.entrySet()) {
    		String filePath = entry.getKey();
    		if (current.containsKey(filePath)) {
    			if (!current.get(filePath).equals(before.get(filePath))) {
    				modifiedFiles.add(filePath);
    			}
    		} else {
    			deletedFiles.add(filePath);
    		}
    	}
    	for(Entry<String, Long> entry : current.entrySet()) {
    		String filePath = entry.getKey();
    		if (!before.containsKey(filePath)) {
    			addedFiles.add(filePath);
    		}
    	}
    	listener.getLogger().println("Check modification in " + input);
    	
    	listener.getLogger().println("Added: " + addedFiles);
    	listener.getLogger().println("Modified: " + modifiedFiles);
    	listener.getLogger().println("Deleted: " + deletedFiles);

        // Save current states
        JSONObject json = new JSONObject();
        json.put("files", current);
        build.getWorkspace().child("files.txt").write(json.toString(), "utf-8");
        
        return true;
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link HelloWorldBuilder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>src/main/resources/hudson/plugins/hello_world/HelloWorldBuilder/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        /**
         * To persist global configuration information,
         * simply store it in a field and call save().
         *
         * <p>
         * If you don't want fields to be persisted, use <tt>transient</tt>.
         */
        private boolean useFrench;

        /**
         * Performs on-the-fly validation of the form field 'name'.
         *
         * @param value
         *      This parameter receives the value that the user has typed.
         * @return
         *      Indicates the outcome of the validation. This is sent to the browser.
         */
        public FormValidation doCheckInput(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please specify the full path of the directory to check modification.");
            if (value.length() < 4)
                return FormValidation.warning("Isn't the path too short?");
            return FormValidation.ok();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Check updated files";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // set that to properties and call save().
            useFrench = formData.getBoolean("useFrench");
            // ^Can also use req.bindJSON(this, formData);
            //  (easier when there are many fields; need set* methods for this, like setUseFrench)
            save();
            return super.configure(req,formData);
        }

        /**
         * This method returns true if the global configuration says we should speak French.
         *
         * The method name is bit awkward because global.jelly calls this method to determine
         * the initial state of the checkbox by the naming convention.
         */
        public boolean getUseFrench() {
            return useFrench;
        }
    }
}

