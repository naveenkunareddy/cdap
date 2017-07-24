/*
 * Copyright © 2014 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package co.cask.cdap.app.twill;

import co.cask.cdap.app.runtime.ProgramOptions;
import co.cask.cdap.app.runtime.ProgramStateWriter;
import co.cask.cdap.internal.app.runtime.ProgramOptionConstants;
import co.cask.cdap.internal.app.runtime.distributed.TwillAppNames;
import com.google.common.collect.ImmutableMap;
import org.apache.twill.api.EventHandler;
import org.apache.twill.api.EventHandlerContext;
import org.apache.twill.api.RunId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A Twill {@link EventHandler} that responds to Twill application lifecycle events and abort the application if
 * it cannot provision container for some runnable
 */
public class TwillAppLifecycleEventHandler extends EventHandler {

  private static final Logger LOG = LoggerFactory.getLogger(TwillAppLifecycleEventHandler.class);

  private long abortTime;
  private boolean abortIfNotFull;
  private final ProgramStateWriter programStateWriter;
  private final ProgramOptions options;

  /**
   * Constructs an instance of TwillAppLifecycleEventHandler that abort the application if some runnable has no
   * containers, same as calling
   * {@link #TwillAppLifecycleEventHandler(long, boolean, ProgramStateWriter, ProgramOptions)} with second
   * parameter as {@code false}.
   *
   * @param abortTime Time in milliseconds to pass before aborting the application if no container is given to
   *                  a runnable.
   */
  public TwillAppLifecycleEventHandler(long abortTime, ProgramStateWriter programStateWriter, ProgramOptions options) {
    this(abortTime, false, programStateWriter, options);
  }

  /**
   * Constructs an instance of TwillAppLifecycleEventHandler that abort the application if some runnable has not enough
   * containers.
   * @param abortTime Time in milliseconds to pass before aborting the application if no container is given to
   *                  a runnable.
   * @param abortIfNotFull If {@code true}, it will abort the application if any runnable doesn't meet the expected
   *                       number of instances.
   * @param programStateWriter
   */
  public TwillAppLifecycleEventHandler(long abortTime, boolean abortIfNotFull, ProgramStateWriter programStateWriter,
                                       ProgramOptions options) {
    this.abortTime = abortTime;
    this.abortIfNotFull = abortIfNotFull;
    this.programStateWriter = programStateWriter;
    this.options = options;
  }

  @Override
  protected Map<String, String> getConfigs() {
    return ImmutableMap.of("abortTime", Long.toString(abortTime),
                           "abortIfNotFull", Boolean.toString(abortIfNotFull));
  }

  @Override
  public void initialize(EventHandlerContext context) {
    super.initialize(context);
    this.abortTime = Long.parseLong(context.getSpecification().getConfigs().get("abortTime"));
    this.abortIfNotFull = Boolean.parseBoolean(context.getSpecification().getConfigs().get("abortIfNotFull"));
  }


  @Override
  public TimeoutAction launchTimeout(Iterable<TimeoutEvent> timeoutEvents) {
    long now = System.currentTimeMillis();
    for (TimeoutEvent event : timeoutEvents) {
      LOG.info("Requested {} containers for runnable {}, only got {} after {} ms.",
               event.getExpectedInstances(), event.getRunnableName(),
               event.getActualInstances(), System.currentTimeMillis() - event.getRequestTime());

      boolean pass = abortIfNotFull ? event.getActualInstances() == event.getExpectedInstances()
                                    : event.getActualInstances() != 0;
      if (!pass && (now - event.getRequestTime()) > abortTime) {
        LOG.info("No containers for {}. Abort the application.", event.getRunnableName());
        return TimeoutAction.abort();
      }
    }
    // Check again in half of abort time.
    return TimeoutAction.recheck(abortTime / 2, TimeUnit.MILLISECONDS);
  }

  public void started(String twillAppName, RunId twillRunId) {
    if (options == null) {
      return;
    }
    String runId = options.getArguments().getOption(ProgramOptionConstants.RUN_ID);
    programStateWriter.running(TwillAppNames.fromTwillAppName(twillAppName).run(runId), twillRunId.getId());
  }

  public void containerLaunched(String twillAppName, RunId twillRunId, int instanceId) {
    // no-op
  }

  public void completed(String twillAppName, RunId twillRunId) {
    if (options == null) {
      return;
    }
    String runId = options.getArguments().getOption(ProgramOptionConstants.RUN_ID);
    programStateWriter.completed(TwillAppNames.fromTwillAppName(twillAppName).run(runId));
  }

  public void killed(String twillAppName, RunId twillRunId) {
    if (options == null) {
      return;
    }
    String runId = options.getArguments().getOption(ProgramOptionConstants.RUN_ID);
    programStateWriter.killed(TwillAppNames.fromTwillAppName(twillAppName).run(runId));
  }
}
