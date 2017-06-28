/*
 * Copyright © 2017 Cask Data, Inc.
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

package co.cask.cdap.internal.app.program;

import co.cask.cdap.app.runtime.ProgramController;
import co.cask.cdap.app.runtime.ProgramStateWriter;
import co.cask.cdap.common.app.RunIds;
import co.cask.cdap.internal.app.runtime.AbstractListener;
import co.cask.cdap.internal.app.runtime.AbstractProgramController;
import co.cask.cdap.proto.ProgramRunStatus;
import co.cask.cdap.proto.id.ProgramRunId;
import com.google.common.util.concurrent.Service;
import org.apache.twill.common.Threads;
import org.apache.twill.internal.ServiceListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * Abstract implementation of {@link AbstractProgramController} that responds to state transitions and persists all
 * state changes.
 */
public abstract class AbstractStateChangeProgramController extends AbstractProgramController {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractStateChangeProgramController.class);

  public AbstractStateChangeProgramController(Service service, final ProgramRunId programRunId, final String twillRunId,
                                              final ProgramStateWriter programStateWriter,
                                              @Nullable String componentName) {
    super(programRunId.getParent(), RunIds.fromString(programRunId.getRun()), componentName);

    service.addListener(
      new ServiceListenerAdapter() {
        @Override
        public void starting() {
          programStateWriter.running(programRunId, twillRunId);
        }

        @Override
        public void terminated(Service.State from) {
          ProgramRunStatus runStatus = ProgramController.State.COMPLETED.getRunStatus();
          if (from == Service.State.STOPPING) {
            // Service was killed
            runStatus = ProgramController.State.KILLED.getRunStatus();
          }
          switch (runStatus) {
            case COMPLETED:
              programStateWriter.completed(programRunId);
              break;
            case KILLED:
              programStateWriter.killed(programRunId);
              break;
            default:
              // Will never reach this position
              throw new UnsupportedOperationException(String.format("Cannot terminate program {} for status {}",
                                                                    programRunId, runStatus));
          }
        }

        @Override
        public void failed(Service.State from, @Nullable final Throwable failure) {
          programStateWriter.error(programRunId, failure);
        }
      },
      Threads.SAME_THREAD_EXECUTOR
    );
  }

  public AbstractStateChangeProgramController(final ProgramRunId programRunId, final String twillRunId,
                                              final ProgramStateWriter programStateWriter,
                                              @Nullable String componentName) {
    super(programRunId.getParent(), RunIds.fromString(programRunId.getRun()), componentName);

    addListener(
      new AbstractListener() {
        @Override
        public void init(ProgramController.State state, @Nullable Throwable cause) {
          switch(state) {
            case ALIVE:
              alive();
              break;
            case COMPLETED:
              completed();
              break;
            case ERROR:
              error(cause);
              break;
            default:
              super.init(state, cause);
          }
        }

        @Override
        public void alive() {
          programStateWriter.running(programRunId, twillRunId);
        }

        @Override
        public void completed() {
          LOG.debug("Program {} completed successfully.", programRunId);
          programStateWriter.completed(programRunId);
        }

        @Override
        public void killed() {
          LOG.debug("Program {} killed.", programRunId);
          programStateWriter.killed(programRunId);
        }

        @Override
        public void suspended() {
          LOG.debug("Suspending Program {} .", programRunId);
          programStateWriter.suspend(programRunId);
        }

        @Override
        public void resuming() {
          LOG.debug("Resuming Program {}.", programRunId);
          programStateWriter.resume(programRunId);
        }

        @Override
        public void error(Throwable cause) {
          LOG.info("Program {} stopped with error: {}", programRunId, cause);
          programStateWriter.error(programRunId, cause);
        }
      },
      Threads.SAME_THREAD_EXECUTOR
    );
  }
}
