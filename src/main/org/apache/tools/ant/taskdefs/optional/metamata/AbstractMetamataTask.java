/*
 * Copyright  2001-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.apache.tools.ant.taskdefs.optional.metamata;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.Execute;
import org.apache.tools.ant.taskdefs.ExecuteStreamHandler;
import org.apache.tools.ant.types.Commandline;
import org.apache.tools.ant.types.CommandlineJava;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.util.FileUtils;
import org.apache.tools.ant.util.JavaEnvUtils;

/**
 * Somewhat abstract framework to be used for other metama 2.0 tasks.
 * This should include, audit, metrics, cover and mparse.
 *
 * For more information, visit the website at
 * <a href="http://www.metamata.com">www.metamata.com</a>
 *
 */
public abstract class AbstractMetamataTask extends Task {

    /**
     * The user classpath to be provided. It matches the -classpath of the
     * command line. The classpath must includes both the <tt>.class</tt> and the
     * <tt>.java</tt> files for accurate audit.
     */
    protected Path classPath = null;

    /** the path to the source file */
    protected Path sourcePath = null;

    /**
     * Metamata home directory. It will be passed as a <tt>metamata.home</tt> property
     * and should normally matches the environment property <tt>META_HOME</tt>
     * set by the Metamata installer.
     */
    protected File metamataHome = null;

    /** the command line used to run MAudit */
    protected CommandlineJava cmdl = new CommandlineJava();

    /** the set of files to be audited */
    protected Vector fileSets = new Vector();

    /** the options file where are stored the command line options */
    protected File optionsFile = null;

    // this is used to keep track of which files were included. It will
    // be set when calling scanFileSets();
    protected Hashtable includedFiles = null;

    public AbstractMetamataTask() {
    }

    /** initialize the task with the classname of the task to run */
    protected AbstractMetamataTask(String className) {
        cmdl.setVm(JavaEnvUtils.getJreExecutable("java"));
        cmdl.setClassname(className);
    }

    /**
     * the metamata.home property to run all tasks.
     * @ant.attribute ignore="true"
     */
    public void setHome(final File value) {
        this.metamataHome = value;
    }

    /**
     * The home directory containing the Metamata distribution; required
     */
    public void setMetamatahome(final File value) {
        setHome(value);
    }

    /**
     * Sets the class path (also source path unless one explicitly set).
     * Overrides METAPATH/CLASSPATH environment variables.
     */
    public Path createClasspath() {
        if (classPath == null) {
            classPath = new Path(getProject());
        }
        return classPath;
    }

    /**
     * Sets the source path.
     * Overrides the SOURCEPATH environment variable.
     */
    public Path createSourcepath() {
        if (sourcePath == null) {
            sourcePath = new Path(getProject());
        }
        return sourcePath;
    }

    /**
     * Additional optional parameters to pass to the JVM.
     * You can avoid using the  <code>&lt;jvmarg&gt;</code> by adding these empty
     * entries to <code>metamata.properties</code> located at <code>${metamata.home}/bin</code>
     *
     * <pre>metamata.classpath=
     * metamata.sourcepath=
     * metamata.baseclasspath=
     * </pre>
     */
    public Commandline.Argument createJvmarg() {
        return cmdl.createVmArgument();
    }

    /**
     * Set the maximum memory for the JVM; optional.
     * -mx or -Xmx depending on VM version
     */
    public void setMaxmemory(String max) {
        cmdl.setMaxmemory(max);
    }


    /**
     * The java files or directory to audit.
     * Whatever the filter is, only the files that end
     * with .java will be included for processing.
     * Note that the base directory used for the fileset
     * MUST be the root of the source files otherwise package names
     * deduced from the file path will be incorrect.
     */
    public void addFileSet(FileSet fs) {
        fileSets.addElement(fs);
    }

    /** execute the command line */
    public void execute() throws BuildException {
        try {
            setUp();
            ExecuteStreamHandler handler = createStreamHandler();
            execute0(handler);
        } finally {
            cleanUp();
        }
    }

    /** check the options and build the command line */
    protected void setUp() throws BuildException {
        checkOptions();

        // set the classpath as the jar file
        File jar = getMetamataJar(metamataHome);
        final Path cp = cmdl.createClasspath(getProject());
        cp.createPathElement().setLocation(jar);

        // set the metamata.home property
        final Commandline.Argument vmArgs = cmdl.createVmArgument();
        vmArgs.setValue("-Dmetamata.home=" + metamataHome.getAbsolutePath());

        // retrieve all the files we want to scan
        includedFiles = scanSources(new Hashtable());
        //String[] entries = sourcePath.list();
        //includedFiles = scanSources(new Hashtable(), entries);
        log(includedFiles.size() + " files added for audit", Project.MSG_VERBOSE);

        // write all the options to a temp file and use it ro run the process
        Vector options = getOptions();
        optionsFile = createTmpFile();
        generateOptionsFile(optionsFile, options);
        Commandline.Argument args = cmdl.createArgument();
        args.setLine("-arguments " + optionsFile.getAbsolutePath());
    }

    /**
     * create a stream handler that will be used to get the output since
     * metamata tools do not report with convenient files such as XML.
     */
    protected abstract ExecuteStreamHandler createStreamHandler();


    /** execute the process with a specific handler */
    protected void execute0(ExecuteStreamHandler handler) throws BuildException {
        final Execute process = new Execute(handler);
        log(cmdl.describeCommand(), Project.MSG_VERBOSE);
        process.setCommandline(cmdl.getCommandline());
        try {
            if (process.execute() != 0) {
                throw new BuildException("Metamata task failed.");
            }
        } catch (IOException e) {
            throw new BuildException("Failed to launch Metamata task", e);
        }
    }

    /** clean up all the mess that we did with temporary objects */
    protected void cleanUp() {
        if (optionsFile != null) {
            optionsFile.delete();
            optionsFile = null;
        }
    }

    /** return the location of the jar file used to run */
    protected final File getMetamataJar(File home) {
        return new File(home, "lib/metamata.jar");
    }

    /** validate options set */
    protected void checkOptions() throws BuildException {
        // do some validation first
        if (metamataHome == null || !metamataHome.exists()) {
            throw new BuildException("'home' must point to Metamata home directory.");
        }
        File jar = getMetamataJar(metamataHome);
        if (!jar.exists()) {
            throw new BuildException(jar + " does not exist. Check your metamata installation.");
        }
    }

    /** return all options of the command line as string elements */
    protected abstract Vector getOptions();


    protected void generateOptionsFile(File tofile, Vector options) throws BuildException {
        FileWriter fw = null;
        try {
            fw = new FileWriter(tofile);
            PrintWriter pw = new PrintWriter(fw);
            final int size = options.size();
            for (int i = 0; i < size; i++) {
                pw.println(options.elementAt(i));
            }
            pw.flush();
        } catch (IOException e) {
            throw new BuildException("Error while writing options file " + tofile, e);
        } finally {
            if (fw != null) {
                try {
                    fw.close();
                } catch (IOException ignored) {
                }
            }
        }
    }


    protected Hashtable getFileMapping() {
        return includedFiles;
    }

    /**
     * convenient method for JDK 1.1. Will copy all elements from src to dest
     */
    protected static final void addAllVector(Vector dest, Enumeration files) {
        while (files.hasMoreElements()) {
            dest.addElement(files.nextElement());
        }
    }

    protected final File createTmpFile() {
        File tmpFile = FileUtils.newFileUtils()
            .createTempFile("metamata", ".tmp", getProject().getBaseDir());
        tmpFile.deleteOnExit();
        return tmpFile;
    }

    /**
     * @return the list of .java files (as their absolute path) that should
     *         be audited.
     */

    protected Hashtable scanSources(Hashtable map) {
        Hashtable files = new Hashtable();
        for (int i = 0; i < fileSets.size(); i++) {
            FileSet fs = (FileSet) fileSets.elementAt(i);
            DirectoryScanner ds = fs.getDirectoryScanner(getProject());
            ds.scan();
            String[] f = ds.getIncludedFiles();
            log(i + ") Adding " + f.length + " files from directory "
                + ds.getBasedir(), Project.MSG_VERBOSE);
            for (int j = 0; j < f.length; j++) {
                String pathname = f[j];
                if (pathname.endsWith(".java")) {
                    File file = new File(ds.getBasedir(), pathname);
//                  file = project.resolveFile(file.getAbsolutePath());
                    String classname = pathname.substring(0, pathname.length() - ".java".length());
                    classname = classname.replace(File.separatorChar, '.');
                    files.put(file.getAbsolutePath(), classname); // it's a java file, add it.
                }
            }
        }
        return files;
    }

    protected Hashtable scanSources(final Hashtable mapping, final String[] entries) {
        final Vector javaFiles = new Vector(512);
        for (int i = 0; i < entries.length; i++) {
            final File f = new File(entries[i]);
            if (f.isDirectory()) {
                DirectoryScanner ds = new DirectoryScanner();
                ds.setBasedir(f);
                ds.setIncludes(new String[]{"**/*.java"});
                ds.scan();
                String[] included = ds.getIncludedFiles();
                for (int j = 0; j < included.length; j++) {
                    javaFiles.addElement(new File(f, included[j]));
                }
            } else if (entries[i].endsWith(".java")) {
                javaFiles.addElement(f);
            }
        }
        // do the mapping paths/classname
        final int count = javaFiles.size();
        for (int i = 0; i < count; i++) {
            File file = (File) javaFiles.elementAt(i);
            String pathname = Path.translateFile(file.getAbsolutePath());
            String classname = pathname.substring(0, pathname.length() - ".java".length());
            classname = classname.replace(File.separatorChar, '.');
            mapping.put(pathname, classname);
        }
        return mapping;
    }

}
