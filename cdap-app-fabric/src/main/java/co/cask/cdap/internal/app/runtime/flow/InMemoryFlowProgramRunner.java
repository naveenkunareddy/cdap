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

package co.cask.cdap.internal.app.runtime.flow;

import co.cask.cdap.app.program.Program;
import co.cask.cdap.app.runtime.ProgramController;
import co.cask.cdap.app.runtime.ProgramOptions;
import co.cask.cdap.app.runtime.ProgramRunner;
import co.cask.cdap.app.runtime.ProgramStateWriter;
import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.internal.app.AbstractInMemoryProgramRunner;
import co.cask.cdap.internal.app.runtime.worker.WorkerProgramRunner;
import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * Created by sameetsapra on 7/21/17.
 */
public class InMemoryFlowProgramRunner extends AbstractInMemoryProgramRunner {
  private final Provider<FlowProgramRunner> flowProgramRunnerProvider;

  @Inject
  public InMemoryFlowProgramRunner(CConfiguration cConf, ProgramStateWriter programStateWriter,
                                   Provider<FlowProgramRunner> flowProgramRunnerProvider) {
    super(cConf, programStateWriter);
    this.flowProgramRunnerProvider = flowProgramRunnerProvider;
  }

  @Override
  public ProgramController run(Program program, ProgramOptions options) {
    ProgramRunner runner = createProgramRunner();
    return addListener(runner.run(program, options));
  }

  @Override
  protected ProgramRunner createProgramRunner() {
    return flowProgramRunnerProvider.get();
  }
}

