/*
 * Copyright 2012-2013 Continuuity,Inc. All Rights Reserved.
 */

package com.continuuity.app.deploy;

import com.continuuity.api.Application;
import com.continuuity.api.ApplicationSpecification;
import com.continuuity.jar.JarClassLoader;
import com.continuuity.internal.app.ApplicationSpecificationAdapter;
import com.continuuity.internal.io.ReflectionSchemaGenerator;
import com.continuuity.security.ApplicationSecurity;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FilePermission;
import java.io.IOException;
import java.io.Writer;

/**
 * Sandbox JVM allows the configuration phase of an application to be executed
 * within a contained JVM within minimal access to JVM capabilities.
 * <p>
 *   Idea is that this piece of code is called in during the configuration phase
 *   which happens during deployment and running in the same JVM as the server
 *   could be dangerous. Hence, we spin-up a JVM with restricted access to resources
 *   and invoke configure on application.
 * </p>
 */
public class SandboxJVM {
  private static final Logger LOG = LoggerFactory.getLogger(SandboxJVM.class);

  /**
   * Main class within the object.
   * @param args specified on command line.
   * @return 0 if successfull; otherwise non-zero.
   */
  public int doMain(String[] args) {
    String jarFilename;
    File outputFile;

    CommandLineParser parser = new GnuParser();
    Options options = new Options();
    options.addOption("j", "jar", true, "Application JAR");
    options.addOption("o", "output", true, "Output");

    // Check all the options of command line
    try {
      CommandLine line = parser.parse(options, args);
      if(! line.hasOption("jar")) {
        LOG.error("Application JAR not specified.");
        return -1;
      }
      if(! line.hasOption("output")) {
        LOG.error("Output file not specified.");
        return -1;
      }

      jarFilename = line.getOptionValue("jar");
      outputFile = new File(line.getOptionValue("output"));
    } catch (org.apache.commons.cli.ParseException e) {
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp("SandboxJVM", options);
      return -1;
    }

    // Load the JAR using the JAR class load and load the manifest file.
    Object mainClass;
    try {
      JarClassLoader loader = new JarClassLoader(jarFilename);
      mainClass = loader.getMainClass(Application.class);
    } catch (Exception e) {
      LOG.error(e.getMessage());
      return -1;
    }

    // Convert it to the type application.
    Application application = (Application) mainClass;

    // Now, we are ready to call configure on application.
    // Setup security manager, this setting allows only output file to be written.
    // Nothing else can be done from here on other than creating that file.
    ApplicationSecurity.builder()
      .add(new FilePermission(outputFile.getAbsolutePath(), "write"))
      .apply();

    // Now, we call configure, which returns application specification.
    ApplicationSpecification specification = application.configure();

    // Convert the specification to JSON.
    // We write the Application specification to output file in JSON format.
    try {
      Writer writer = Files.newWriter(outputFile, Charsets.UTF_8);
      try {
        // TODO: The SchemaGenerator should be injected.
        ApplicationSpecificationAdapter.create(new ReflectionSchemaGenerator()).toJson(specification, writer);
      } finally {
        writer.close();
      }
    } catch (IOException e) {
      LOG.error("Error writing to file {}. {}", outputFile, e.getMessage());
      return -1;
    }

    return 0;
  }

  /**
   * Invoked from command line.
   * @param args specified on command line.
   */
  public static void main(String[] args) {
    SandboxJVM sandboxJVM = new SandboxJVM();
    System.exit(sandboxJVM.doMain(args));
  }
}
